#include "GoogleVideoSabrClient.hpp"

#include <sstream>

namespace nitroplayer::sabr {

namespace {

std::string escapeJson(const std::string& input) {
  std::string out;
  out.reserve(input.size());
  for (const char c : input) {
    switch (c) {
      case '\\':
        out += "\\\\";
        break;
      case '"':
        out += "\\\"";
        break;
      case '\n':
        out += "\\n";
        break;
      case '\r':
        out += "\\r";
        break;
      case '\t':
        out += "\\t";
        break;
      default:
        out += c;
        break;
    }
  }
  return out;
}

}  // namespace

std::vector<uint8_t> GoogleVideoSabrClient::createRequestPayload(
    const GoogleVideoSabrRequest& request) {
  std::ostringstream json;
  json << '{'
       << "\"serverUrl\":\"" << escapeJson(request.serverUrl) << "\","
       << "\"ustreamerConfigBase64\":\"" << escapeJson(request.ustreamerConfigBase64) << "\","
       << "\"poToken\":\"" << escapeJson(request.poToken) << "\","
       << "\"client\":{\"name\":\"" << escapeJson(request.clientName)
       << "\",\"version\":\"" << escapeJson(request.clientVersion) << "\"},"
       << "\"itags\":[";

  for (size_t i = 0; i < request.selectedItags.size(); ++i) {
    if (i > 0) {
      json << ',';
    }
    json << request.selectedItags[i];
  }

  json << "]}";
  const std::string payload = json.str();
  return std::vector<uint8_t>(payload.begin(), payload.end());
}

}  // namespace nitroplayer::sabr

