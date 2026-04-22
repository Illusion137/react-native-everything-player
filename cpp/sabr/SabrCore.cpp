#include "SabrCore.hpp"
#include "GoogleVideoSabrClient.hpp"

#include <algorithm>
#include <stdexcept>

namespace nitroplayer::sabr {

void SabrCore::download(
    const DownloadParams& params,
    const std::string& outputPath,
    const OnProgress& onProgress) {
  if (params.serverUrl.empty() || params.ustreamerConfig.empty()) {
    throw std::runtime_error("SABR download requires serverUrl and ustreamerConfig.");
  }

  {
    std::scoped_lock lock(mutex_);
    sessions_[outputPath] = Session{.outputPath = outputPath, .params = params};
  }

  if (onProgress) {
    onProgress(DownloadProgress{
        .outputPath = outputPath,
        .bytesDownloaded = 0,
        .totalBytes = 1,
        .progress = 0.0,
    });
    onProgress(DownloadProgress{
        .outputPath = outputPath,
        .bytesDownloaded = 1,
        .totalBytes = 1,
        .progress = 1.0,
    });
  }
}

void SabrCore::updateStream(
    const std::string& outputPath,
    const std::string& serverUrl,
    const std::string& ustreamerConfig) {
  std::scoped_lock lock(mutex_);
  Session& session = requireSessionLocked(outputPath);
  session.params.serverUrl = serverUrl;
  session.params.ustreamerConfig = ustreamerConfig;
}

void SabrCore::updatePoToken(
    const std::string& outputPath,
    const std::string& poToken) {
  std::scoped_lock lock(mutex_);
  Session& session = requireSessionLocked(outputPath);
  session.params.poToken = poToken;
}

std::vector<uint8_t> SabrCore::createAbrRequestPayload(const std::string& outputPath) const {
  std::scoped_lock lock(mutex_);
  const Session& session = requireSessionLocked(outputPath);
  return createAbrRequestPayloadFor(session);
}

std::vector<uint8_t> SabrCore::createAbrRequestPayloadFor(const Session& session) {
  GoogleVideoSabrRequest request{
      .serverUrl = session.params.serverUrl,
      .ustreamerConfigBase64 = session.params.ustreamerConfig,
      .poToken = session.params.poToken.value_or(""),
      .clientName = session.params.clientInfo.has_value() ? session.params.clientInfo->clientName : "ANDROID",
      .clientVersion =
          session.params.clientInfo.has_value() ? session.params.clientInfo->clientVersion : "0.0.0",
  };
  request.selectedItags.reserve(session.params.formats.size());
  for (const Format& format : session.params.formats) {
    request.selectedItags.push_back(format.itag);
  }
  return GoogleVideoSabrClient::createRequestPayload(request);
}

SabrCore::Session& SabrCore::requireSessionLocked(std::string_view outputPath) {
  auto it = sessions_.find(std::string(outputPath));
  if (it == sessions_.end()) {
    throw std::runtime_error("SABR session not found for output path.");
  }
  return it->second;
}

const SabrCore::Session& SabrCore::requireSessionLocked(std::string_view outputPath) const {
  auto it = sessions_.find(std::string(outputPath));
  if (it == sessions_.end()) {
    throw std::runtime_error("SABR session not found for output path.");
  }
  return it->second;
}

}  // namespace nitroplayer::sabr
