#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace nitroplayer::sabr {

struct GoogleVideoSabrRequest {
  std::string serverUrl;
  std::string ustreamerConfigBase64;
  std::string poToken;
  std::string clientName;
  std::string clientVersion;
  std::vector<int> selectedItags;
};

class GoogleVideoSabrClient {
 public:
  [[nodiscard]] static std::vector<uint8_t> createRequestPayload(const GoogleVideoSabrRequest& request);
};

}  // namespace nitroplayer::sabr

