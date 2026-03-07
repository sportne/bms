#include "acme/telemetry/packet/Packet.hpp"

#include <stdexcept>

namespace acme {
namespace telemetry {
namespace packet {

std::vector<std::uint8_t> Packet::encode() const {
  throw std::runtime_error("Encode is not implemented in foundation v1.");
}

/**
 * Decodes a message instance from binary input.
 *
 * @param data encoded message bytes
 * @return decoded Packet value
 */
Packet Packet::decode(std::span<const std::uint8_t> data) {
  (void)data;
  throw std::runtime_error("Decode is not implemented in foundation v1.");
}

}  // namespace packet
}  // namespace telemetry
}  // namespace acme
