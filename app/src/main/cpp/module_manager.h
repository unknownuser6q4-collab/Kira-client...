#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace moonlight {

/**
 * Thread-safe mirror of the Kotlin module catalogue.
 *
 * Kotlin stays authoritative for persistence; this class exists so the native side
 * can answer "is X on?" without a JNI round trip per query, and so the scheduler has
 * a stable, locked view of the enabled set.
 */
class ModuleManager {
public:
    struct Entry {
        std::string id;
        bool enabled = false;
        float value = 0.0f;
        uint64_t toggles = 0;
    };

    /** Registers an id, or returns false when it is already known. */
    bool add(const std::string& id);

    bool setEnabled(const std::string& id, bool enabled);

    bool enabled(const std::string& id) const;

    bool setValue(const std::string& id, float value);

    float value(const std::string& id) const;

    uint64_t toggles(const std::string& id) const;

    std::size_t count() const;

    std::size_t enabledCount() const;

    std::vector<std::string> enabledIds() const;

    /** Drops every entry. Used when the Kotlin config is reset or re-imported. */
    void clear();

private:
    mutable std::mutex mutex_;
    std::unordered_map<std::string, Entry> entries_;
    std::vector<std::string> order_;
};

}  // namespace moonlight
