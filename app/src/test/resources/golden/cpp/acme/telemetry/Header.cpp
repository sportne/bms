#include "acme/telemetry/Header.hpp"

#include <stdexcept>

namespace acme {
namespace telemetry {

std::vector<std::uint8_t> Header::encode() const {
  throw std::runtime_error("Encode is not implemented in foundation v1.");
}

/**
 * Decodes a message instance from binary input.
 *
 * @param data encoded message bytes
 * @return decoded Header value
 */
Header Header::decode(std::span<const std::uint8_t> data) {
  (void)data;
  throw std::runtime_error("Decode is not implemented in foundation v1.");
}

}  // namespace telemetry
}  // namespace acme
