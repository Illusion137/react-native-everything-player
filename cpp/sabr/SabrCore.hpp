#pragma once

#include <functional>
#include <mutex>
#include <optional>
#include <string_view>
#include <string>
#include <unordered_map>
#include <vector>

namespace nitroplayer::sabr {

enum class TokenReason {
  Expired,
  Missing,
  ServerRejected,
  RequiresReauth,
  Unknown,
};

struct ClientInfo {
  std::string clientName;
  std::string clientVersion;
  std::optional<std::string> osName;
  std::optional<std::string> osVersion;
  std::optional<std::string> deviceModel;
};

struct Format {
  int itag;
  std::string mimeType;
  int bitrate;
  std::optional<int> width;
  std::optional<int> height;
  std::optional<int> fps;
};

struct DownloadParams {
  std::string serverUrl;
  std::string ustreamerConfig;
  std::optional<std::string> poToken;
  std::optional<std::string> cookie;
  std::optional<ClientInfo> clientInfo;
  std::vector<Format> formats;
  bool preferOpus;
};

struct DownloadProgress {
  std::string outputPath;
  int64_t bytesDownloaded;
  int64_t totalBytes;
  double progress;
};

class SabrCore {
 public:
  using OnProgress = std::function<void(const DownloadProgress&)>;

  void download(const DownloadParams& params, const std::string& outputPath, const OnProgress& onProgress);
  void updateStream(const std::string& outputPath, const std::string& serverUrl, const std::string& ustreamerConfig);
  void updatePoToken(const std::string& outputPath, const std::string& poToken);

  [[nodiscard]] std::vector<uint8_t> createAbrRequestPayload(const std::string& outputPath) const;

 private:
  struct Session {
    std::string outputPath;
    DownloadParams params;
  };

  [[nodiscard]] static std::vector<uint8_t> createAbrRequestPayloadFor(const Session& session);
  [[nodiscard]] Session& requireSessionLocked(std::string_view outputPath);
  [[nodiscard]] const Session& requireSessionLocked(std::string_view outputPath) const;

  mutable std::mutex mutex_;
  std::unordered_map<std::string, Session> sessions_;
};

}  // namespace nitroplayer::sabr
