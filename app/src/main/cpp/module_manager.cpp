#include "module_manager.h"

#include <algorithm>

namespace moonlight {

bool ModuleManager::add(const std::string& id) {
    if (id.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    if (entries_.find(id) != entries_.end()) {
        return false;
    }
    Entry entry;
    entry.id = id;
    entries_.emplace(id, std::move(entry));
    order_.push_back(id);
    return true;
}

bool ModuleManager::setEnabled(const std::string& id, bool enabled) {
    if (id.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(id);
    if (it == entries_.end()) {
        // Kotlin can legitimately enable something before it registered the id.
        Entry entry;
        entry.id = id;
        it = entries_.emplace(id, std::move(entry)).first;
        order_.push_back(id);
    }
    if (it->second.enabled != enabled) {
        it->second.enabled = enabled;
        it->second.toggles += 1;
    }
    return true;
}

bool ModuleManager::enabled(const std::string& id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(id);
    return it != entries_.end() && it->second.enabled;
}

bool ModuleManager::setValue(const std::string& id, float value) {
    if (id.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(id);
    if (it == entries_.end()) {
        Entry entry;
        entry.id = id;
        it = entries_.emplace(id, std::move(entry)).first;
        order_.push_back(id);
    }
    it->second.value = value;
    return true;
}

float ModuleManager::value(const std::string& id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(id);
    return it == entries_.end() ? 0.0f : it->second.value;
}

uint64_t ModuleManager::toggles(const std::string& id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = entries_.find(id);
    return it == entries_.end() ? 0 : it->second.toggles;
}

std::size_t ModuleManager::count() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return entries_.size();
}

std::size_t ModuleManager::enabledCount() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::size_t total = 0;
    for (const auto& pair : entries_) {
        if (pair.second.enabled) {
            ++total;
        }
    }
    return total;
}

std::vector<std::string> ModuleManager::enabledIds() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<std::string> result;
    result.reserve(entries_.size());
    for (const auto& id : order_) {
        auto it = entries_.find(id);
        if (it != entries_.end() && it->second.enabled) {
            result.push_back(id);
        }
    }
    std::sort(result.begin(), result.end());
    return result;
}

void ModuleManager::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    entries_.clear();
    order_.clear();
}

}  // namespace moonlight
