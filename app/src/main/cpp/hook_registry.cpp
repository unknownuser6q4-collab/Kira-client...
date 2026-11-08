#include "hook_registry.h"

#include <chrono>

namespace moonlight {

int64_t HookRegistry::nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

void HookRegistry::attach(int32_t bridgePid, const std::string& bridgeVersion) {
    std::lock_guard<std::mutex> lock(mutex_);
    bridgePid_ = bridgePid;
    bridgeVersion_ = bridgeVersion;
    lastHeartbeatMs_ = nowMs();
    state_ = State::Attached;
}

void HookRegistry::heartbeat() {
    std::lock_guard<std::mutex> lock(mutex_);
    lastHeartbeatMs_ = nowMs();
    if (state_ != State::Detached) {
        state_ = State::Attached;
    }
}

void HookRegistry::detach() {
    std::lock_guard<std::mutex> lock(mutex_);
    state_ = State::Detached;
    bridgePid_ = 0;
    bridgeVersion_.clear();
    lastHeartbeatMs_ = 0;
}

HookRegistry::State HookRegistry::state() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != State::Attached) {
        return State::Detached;
    }
    if (nowMs() - lastHeartbeatMs_ > kStaleAfterMs) {
        return State::Stale;
    }
    return State::Attached;
}

std::string HookRegistry::describe() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (state_ != State::Attached) {
        return "detached - no bridge in Minecraft's process";
    }
    const int64_t ageMs = nowMs() - lastHeartbeatMs_;
    if (ageMs > kStaleAfterMs) {
        return "stale - last heartbeat " + std::to_string(ageMs / 1000) + "s ago";
    }
    return "attached - bridge pid " + std::to_string(bridgePid_) + ", v" + bridgeVersion_;
}

std::string HookRegistry::bridgeVersion() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return bridgeVersion_;
}

int32_t HookRegistry::bridgePid() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return bridgePid_;
}

}  // namespace moonlight
