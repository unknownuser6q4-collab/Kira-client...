#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

namespace moonlight {

class ModuleManager;
class HookRegistry;

/**
 * Process-local scheduler for the native module layer.
 *
 * The engine owns one fixed-rate worker thread. Its job is to keep the native mirror
 * of the Kotlin module registry resident and to measure itself, which is the only
 * thing it can honestly do: this library loads inside Moonlight's own process, so it
 * cannot read or write Minecraft's memory. Anything that claims to change gameplay
 * has to go through HookRegistry, and that stays DETACHED until a real in-process
 * bridge registers itself and keeps sending heartbeats.
 */
class Engine {
public:
    static Engine& instance();

    /** Starts the worker thread. Idempotent. */
    bool start();

    /** Stops and joins the worker thread. Idempotent. */
    void stop();

    bool running() const;

    ModuleManager& modules();
    HookRegistry& hooks();

    struct Stats {
        uint64_t ticks = 0;
        uint64_t uptimeMs = 0;
        uint64_t overruns = 0;
        uint64_t modules = 0;
        uint64_t enabled = 0;
        double meanMicros = 0.0;
        double peakMicros = 0.0;
    };

    Stats stats();

    std::string version() const;
    std::string abi() const;

    static constexpr int kTickHz = 100;
    static constexpr double kStepMicros = 1000000.0 / static_cast<double>(kTickHz);

private:
    Engine() = default;
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    void loop();
    void measure(double micros);

    std::atomic<bool> running_{false};
    std::atomic<bool> stopRequested_{false};
    std::thread worker_;

    std::atomic<uint64_t> ticks_{0};
    std::atomic<uint64_t> totalNanos_{0};
    std::atomic<uint64_t> peakNanos_{0};
    std::atomic<uint64_t> overruns_{0};

    std::chrono::steady_clock::time_point startedAt_{};
};

}  // namespace moonlight
