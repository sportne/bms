#pragma once

#include <cstdint>
#include <span>
#include <vector>

namespace acme {
namespace telemetry {

struct Header {
  std::uint8_t version{};
  std::uint16_t sequence{};

  std::vector<std::uint8_t> encode() const;
  static Header decode(std::span<const std::uint8_t> data);
};

}  // namespace telemetry
}  // namespace acme
