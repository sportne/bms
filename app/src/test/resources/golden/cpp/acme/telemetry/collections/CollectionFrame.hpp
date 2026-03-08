#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <vector>

namespace acme {
namespace telemetry {
namespace collections {

struct CollectionFrame {
  std::uint16_t count{};
  std::uint8_t blobCount{};
  std::array<std::uint8_t, 4> samples{};
  std::vector<std::uint8_t> events{};
  std::array<std::uint8_t, 8> hash{};
  std::vector<std::uint8_t> payload{};
  std::vector<std::uint8_t> pathData{};
  std::array<std::uint8_t, 2> reusableArrayField{};
  std::vector<std::uint8_t> reusableVectorField{};
  std::array<std::uint8_t, 16> reusableBlobArrayField{};
  std::vector<std::uint8_t> reusableBlobVectorField{};

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
  static CollectionFrame decode(std::span<const std::uint8_t> data);

  /**
   * Decodes one message from the current cursor position.
   *
   * @param data encoded message bytes
   * @param cursor current decode cursor; advanced past this message
   * @return decoded message value
   */
  static CollectionFrame decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor);
};

}  // namespace collections
}  // namespace telemetry
}  // namespace acme
