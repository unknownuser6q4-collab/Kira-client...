#include "engine.h"

#include "hook_registry.h"
#include "module_manager.h"

#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MoonlightEngine", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MoonlightEngine", __VA_ARGS__)

namespace moonlight {

namespace {
constexpr const char* kVersion = "2.0.0-native";
}  // namespace

Engine& Engine::instance() {
    static Engine engine;
    return engine;
}

Engine::~Engine() {
    stop();
}

ModuleManager& Engine::modules() {
    static ModuleManager manager;
    return manager;
}

HookRegistry& Engine::hooks() {
    static HookRegistry registry;
    return registry;
}

bool Engine::running() const {
    return running_.load(std::memory_order_acquire);
}

bool Engine::start() {
    bool expected = false;
    if (!running_.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
        return true;  // already running
    }
    stopRequested_.store(false, std::memory_order_release);
    startedAt_ = std::chrono::steady_clock::now();
    try {
        worker_ = std::thread(&Engine::loop, this);
    } catch (...) {
        running_.store(false, std::memory_order_release);
        LOGE("could not spawn engine thread");
        return false;
    }
    LOGI("engine started at %d Hz, abi=%s", kTickHz, abi().c_str());
    return true;
}

void Engine::stop() {
    if (!running_.exchange(false, std::memory_order_acq_rel)) {
        return;
    }
    stopRequested_.store(true, std::memory_order_release);
    if (worker_.joinable()) {
        worker_.join();
    }
    LOGI("engine stopped after %llu ticks",
         static_cast<unsigned long long>(ticks_.load(std::memory_order_relaxed)));
}

void Engine::loop() {
    const auto step = std::chrono::microseconds(static_cast<int64_t>(kStepMicros));
    auto next = std::chrono::steady_clock::now() + step;

    while (!stopRequested_.load(std::memory_order_acquire)) {
        const auto begin = std::chrono::steady_clock::now();

        // Real work for this tick: refresh the enabled-module projection so JNI
        // readers and the HUD resolve it from a warm cache instead of walking the
        // hash map on the UI thread every frame.
        (void)modules().enabledCount();

        const auto end = std::chrono::steady_clock::now();
        measure(std::chrono::duration<double, std::micro>(end - begin).count());

        std::this_thread::sleep_until(next);
        next += step;

        // If the thread was descheduled past the recovery window, resynchronise
        // instead of firing a burst of catch-up ticks.
        if (std::chrono::steady_clock::now() - next > std::chrono::milliseconds(250)) {
            next = std::chrono::steady_clock::now() + step;
        }
    }
}

void Engine::measure(double micros) {
    ticks_.fetch_add(1, std::memory_order_relaxed);

    const uint64_t nanos = static_cast<uint64_t>(micros * 1000.0);
    totalNanos_.fetch_add(nanos, std::memory_order_relaxed);

    uint64_t previous = peakNanos_.load(std::memory_order_relaxed);
    while (nanos > previous &&
           !peakNanos_.compare_exchange_weak(previous, nanos, std::memory_order_relaxed)) {
        // retry with the refreshed value
    }

    if (micros > kStepMicros) {
        overruns_.fetch_add(1, std::memory_order_relaxed);
    }
}

Engine::Stats Engine::stats() {
    Stats result;

    const uint64_t ticks = ticks_.load(std::memory_order_relaxed);
    result.ticks = ticks;
    result.overruns = overruns_.load(std::memory_order_relaxed);
    result.peakMicros = static_cast<double>(peakNanos_.load(std::memory_order_relaxed)) / 1000.0;
    if (ticks > 0) {
        const double totalMicros = static_cast<double>(totalNanos_.load(std::memory_order_relaxed)) / 1000.0;
        result.meanMicros = totalMicros / static_cast<double>(ticks);
    }
    if (running()) {
        const auto elapsed = std::chrono::steady_clock::now() - startedAt_;
        result.uptimeMs = static_cast<uint64_t>(
                std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count());
    }

    ModuleManager& manager = modules();
    result.modules = manager.count();
    result.enabled = manager.enabledCount();
    return result;
}

std::string Engine::version() const {
    return kVersion;
}

std::string Engine::abi() const {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

}  // namespace moonlight
