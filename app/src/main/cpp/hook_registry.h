#pragma once

#include <cstdint>
#include <mutex>
#include <string>

namespace moonlight {

/**
 * Tracks whether an in-process game bridge is really present.
 *
 * There is no way around this: a normal Android app cannot reach another app's
 * memory, so a bridge only exists when something inside Minecraft's process opens a
 * channel to us and keeps heartbeating. Nothing in this project fakes that. Until a
 * real heartbeat lands, [state] returns Detached and every hook-only module must be
 * reported as not running.
 */
class HookRegistry {
public:
    enum class State : int {
        Detached = 0,
        Attached = 1,
        Stale = 2,
    };

    /** A heartbeat older than this means the bridge died or was unloaded. */
    static constexpr int64_t kStaleAfterMs = 120000;

    /** Called by a bridge that has actually connected. */
    void attach(int32_t bridgePid, const std::string& bridgeVersion);

    /** Called on every heartbeat; refreshes the liveness stamp. */
    void heartbeat();

    void detach();

    State state() const;

    std::string describe() const;

    std::string bridgeVersion() const;

    int32_t bridgePid() const;

private:
    static int64_t nowMs();

    mutable std::mutex mutex_;
    State state_ = State::Detached;
    int64_t lastHeartbeatMs_ = 0;
    int32_t bridgePid_ = 0;
    std::string bridgeVersion_;
};

}  // namespace moonlight
