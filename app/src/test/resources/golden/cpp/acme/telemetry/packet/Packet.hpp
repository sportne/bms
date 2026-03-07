#pragma once

#include <cstdint>
#include <span>
#include <vector>
#include "acme/telemetry/Header.hpp"

namespace acme {
namespace telemetry {
namespace packet {

struct Packet {
  ::acme::telemetry::Header header{};
  std::uint32_t payloadLength{};

  std::vector<std::uint8_t> encode() const;
  static Packet decode(std::span<const std::uint8_t> data);
};

}  // namespace packet
}  // namespace telemetry
}  // namespace acme
