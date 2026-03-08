#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <vector>

namespace acme {
namespace telemetry {
namespace numeric {

struct TelemetryFrame {
  std::uint8_t version{};
  std::uint8_t statusBits{};
  double temperature{};
  double voltage{};
  double reusableTemperature{};
  double reusableFloat{};

  bool getStatusBitsAlarm() const;
  void setStatusBitsAlarm(bool value);
  std::uint64_t getStatusBitsState() const;
  void setStatusBitsState(std::uint64_t value);
  static constexpr std::uint64_t STATUSBITS_STATE_OFF = 0ULL;
  static constexpr std::uint64_t STATUSBITS_STATE_ON = 1ULL;

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
  static TelemetryFrame decode(std::span<const std::uint8_t> data);

  /**
   * Decodes one message from the current cursor position.
   *
   * @param data encoded message bytes
   * @param cursor current decode cursor; advanced past this message
   * @return decoded message value
   */
  static TelemetryFrame decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor);
};

}  // namespace numeric
}  // namespace telemetry
}  // namespace acme
