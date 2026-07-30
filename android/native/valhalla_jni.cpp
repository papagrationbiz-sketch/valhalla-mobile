#include <jni.h>

#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <utility>

#include <valhalla/worker.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>

#include "valhalla_actor.h"

namespace {

constexpr const char* kNativeBridgeClass =
    "com/valhalla/valhalla/ValhallaNative";

enum class Operation {
  kRoute,
  kTraceAttributes,
  kTraceRoute,
};

std::string error_json(const int code, const std::string& message) {
  rapidjson::StringBuffer buffer;
  rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
  writer.StartObject();
  writer.Key("code");
  writer.Int(code);
  writer.Key("message");
  writer.String(message.data(), static_cast<rapidjson::SizeType>(message.size()));
  writer.EndObject();
  return {buffer.GetString(), buffer.GetSize()};
}

std::string current_exception_json(const char* operation) noexcept {
  try {
    throw;
  } catch (const valhalla::valhalla_exception_t& error) {
    return error_json(error.code, error.message);
  } catch (const std::exception& error) {
    return error_json(-1, error.what());
  } catch (...) {
    return error_json(-1, std::string(operation) + ": unknown exception");
  }
}

bool bytes_to_string(JNIEnv* env, jbyteArray bytes, std::string& output) {
  if (bytes == nullptr) {
    output.clear();
    return true;
  }

  const jsize size = env->GetArrayLength(bytes);
  if (env->ExceptionCheck()) {
    return false;
  }

  output.resize(static_cast<std::size_t>(size));
  if (size > 0) {
    env->GetByteArrayRegion(
        bytes, 0, size, reinterpret_cast<jbyte*>(output.data()));
  }
  return !env->ExceptionCheck();
}

jbyteArray string_to_bytes(JNIEnv* env, const std::string& value) {
  if (value.size() >
      static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
    const std::string error =
        error_json(-1, "native response exceeds the JNI byte array limit");
    return string_to_bytes(env, error);
  }

  const auto size = static_cast<jsize>(value.size());
  jbyteArray bytes = env->NewByteArray(size);
  if (bytes == nullptr || env->ExceptionCheck()) {
    return nullptr;
  }
  if (size > 0) {
    env->SetByteArrayRegion(
        bytes, 0, size, reinterpret_cast<const jbyte*>(value.data()));
  }
  return env->ExceptionCheck() ? nullptr : bytes;
}

struct ActorSlot {
  explicit ActorSlot(const std::string& config_path) {
    try {
      actor = std::make_unique<ValhallaActor>(config_path);
    } catch (...) {
      initialization_error = current_exception_json("create");
    }
  }

  std::string invoke(const Operation operation, const std::string& request) {
    std::lock_guard<std::mutex> lock(operation_mutex);
    if (!initialization_error.empty()) {
      return initialization_error;
    }
    if (!actor) {
      return error_json(-1, "Valhalla actor is unavailable");
    }

    try {
      switch (operation) {
        case Operation::kRoute:
          return actor->route(request);
        case Operation::kTraceAttributes:
          return actor->trace_attributes(request);
        case Operation::kTraceRoute:
          return actor->trace_route(request);
      }
    } catch (...) {
      return current_exception_json("operation");
    }
    return error_json(-1, "unsupported Valhalla operation");
  }

  std::mutex operation_mutex;
  std::unique_ptr<ValhallaActor> actor;
  std::string initialization_error;
};

std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<ActorSlot>> registry;
std::atomic<jlong> next_handle{1};

jlong register_slot(std::shared_ptr<ActorSlot> slot) {
  const jlong handle = next_handle.fetch_add(1, std::memory_order_relaxed);
  if (handle <= 0) {
    throw std::overflow_error("Valhalla actor handle registry exhausted");
  }

  std::lock_guard<std::mutex> lock(registry_mutex);
  registry.emplace(handle, std::move(slot));
  return handle;
}

std::shared_ptr<ActorSlot> find_slot(const jlong handle) {
  if (handle <= 0) {
    return {};
  }
  std::lock_guard<std::mutex> lock(registry_mutex);
  const auto found = registry.find(handle);
  return found == registry.end() ? nullptr : found->second;
}

jlong native_create(JNIEnv* env, jobject, jbyteArray config_bytes) noexcept {
  try {
    std::string config_path;
    if (!bytes_to_string(env, config_bytes, config_path)) {
      return 0;
    }
    return register_slot(std::make_shared<ActorSlot>(config_path));
  } catch (...) {
    return 0;
  }
}

void native_destroy(JNIEnv*, jobject, const jlong handle) noexcept {
  try {
    std::shared_ptr<ActorSlot> removed;
    {
      std::lock_guard<std::mutex> lock(registry_mutex);
      const auto found = registry.find(handle);
      if (found == registry.end()) {
        return;
      }
      removed = std::move(found->second);
      registry.erase(found);
    }
  } catch (...) {
    // Destruction is idempotent and must never unwind through JNI.
  }
}

jbyteArray native_invoke(JNIEnv* env,
                         const jlong handle,
                         jbyteArray request_bytes,
                         const Operation operation) noexcept {
  try {
    std::string request;
    if (!bytes_to_string(env, request_bytes, request)) {
      return nullptr;
    }
    const auto slot = find_slot(handle);
    const std::string result =
        slot ? slot->invoke(operation, request)
             : error_json(-1, "Valhalla actor is closed");
    return string_to_bytes(env, result);
  } catch (...) {
    return string_to_bytes(env, current_exception_json("JNI operation"));
  }
}

jbyteArray native_route(
    JNIEnv* env, jobject, const jlong handle, jbyteArray request) noexcept {
  return native_invoke(env, handle, request, Operation::kRoute);
}

jbyteArray native_trace_attributes(
    JNIEnv* env, jobject, const jlong handle, jbyteArray request) noexcept {
  return native_invoke(env, handle, request, Operation::kTraceAttributes);
}

jbyteArray native_trace_route(
    JNIEnv* env, jobject, const jlong handle, jbyteArray request) noexcept {
  return native_invoke(env, handle, request, Operation::kTraceRoute);
}

const JNINativeMethod kNativeMethods[] = {
    {"create", "([B)J", reinterpret_cast<void*>(native_create)},
    {"destroy", "(J)V", reinterpret_cast<void*>(native_destroy)},
    {"route", "(J[B)[B", reinterpret_cast<void*>(native_route)},
    {"traceAttributes",
     "(J[B)[B",
     reinterpret_cast<void*>(native_trace_attributes)},
    {"traceRoute", "(J[B)[B", reinterpret_cast<void*>(native_trace_route)},
};

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return JNI_ERR;
  }

  jclass bridge = env->FindClass(kNativeBridgeClass);
  if (bridge == nullptr) {
    return JNI_ERR;
  }
  const jint result = env->RegisterNatives(
      bridge,
      kNativeMethods,
      static_cast<jint>(sizeof(kNativeMethods) / sizeof(kNativeMethods[0])));
  env->DeleteLocalRef(bridge);
  return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
  std::unordered_map<jlong, std::shared_ptr<ActorSlot>> removed;
  {
    std::lock_guard<std::mutex> lock(registry_mutex);
    removed.swap(registry);
  }
}
