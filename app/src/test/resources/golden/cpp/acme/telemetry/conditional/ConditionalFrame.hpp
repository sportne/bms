#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <vector>

namespace acme {
namespace telemetry {
namespace conditional {

struct ConditionalFrame {
  std::uint8_t nameLength{};
  std::uint16_t reusableLength{};
  std::string inlineName{};
  std::string inlineTag{};
  std::string reusableLabel{};

  /**
   * Encodes this message instance into wire bytes.
   *
   * @return encoded bytes in deterministic field order
   */
  std::vector<std::uint8_t> encode() const;

  /**
   * Decodes one full message from wire bytes.
   *
   * @param data encoded message bytes
   * @return decoded message value
   */
  static ConditionalFrame decode(std::span<const std::uint8_t> data);

  /**
   * Decodes one message from the current cursor position.
   *
   * @param data encoded message bytes
   * @param cursor current decode cursor; advanced past this message
   * @return decoded message value
   */
  static ConditionalFrame decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor);
};

}  // namespace conditional
}  // namespace telemetry
}  // namespace acme
