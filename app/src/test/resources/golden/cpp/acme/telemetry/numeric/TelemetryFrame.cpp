#include "acme/telemetry/numeric/TelemetryFrame.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <string>
#include <type_traits>

namespace acme {
namespace telemetry {
namespace numeric {

namespace {

void requireReadable(
    std::span<const std::uint8_t> data,
    std::size_t cursor,
    std::size_t requiredBytes,
    const char* label) {
  if (cursor + requiredBytes > data.size()) {
    throw std::invalid_argument(
        std::string("Not enough bytes while decoding ") + label + '.');
  }
}

template <typename T>
void writeIntegral(std::vector<std::uint8_t>& out, T value, bool littleEndian) {
  using Unsigned = std::make_unsigned_t<T>;
  Unsigned raw = static_cast<Unsigned>(value);
  constexpr std::size_t kBytes = sizeof(T);
  for (std::size_t index = 0; index < kBytes; index++) {
    std::size_t shiftIndex = littleEndian ? index : (kBytes - 1U - index);
    out.push_back(static_cast<std::uint8_t>((raw >> (shiftIndex * 8U)) & 0xFFU));
  }
}

template <typename T>
T readIntegral(
    std::span<const std::uint8_t> data,
    std::size_t& cursor,
    bool littleEndian,
    const char* label) {
  using Unsigned = std::make_unsigned_t<T>;
  constexpr std::size_t kBytes = sizeof(T);
  requireReadable(data, cursor, kBytes, label);

  Unsigned raw = 0;
  for (std::size_t index = 0; index < kBytes; index++) {
    std::size_t shiftIndex = littleEndian ? index : (kBytes - 1U - index);
    raw |= static_cast<Unsigned>(data[cursor + index]) << (shiftIndex * 8U);
  }
  cursor += kBytes;
  return static_cast<T>(raw);
}

std::uint16_t floatToHalfBits(float value) {
  std::uint32_t bits = 0;
  std::memcpy(&bits, &value, sizeof(bits));
  std::uint32_t sign = (bits >> 31U) & 0x1U;
  std::int32_t exponent = static_cast<std::int32_t>((bits >> 23U) & 0xFFU) - 127 + 15;
  std::uint32_t mantissa = bits & 0x7FFFFFU;

  if (exponent <= 0) {
    if (exponent < -10) {
      return static_cast<std::uint16_t>(sign << 15U);
    }
    mantissa |= 0x800000U;
    std::uint32_t shifted = mantissa >> static_cast<std::uint32_t>(1 - exponent + 13);
    if ((mantissa >> static_cast<std::uint32_t>(1 - exponent + 12)) & 0x1U) {
      shifted += 1U;
    }
    return static_cast<std::uint16_t>((sign << 15U) | shifted);
  }
  if (exponent >= 31) {
    return static_cast<std::uint16_t>((sign << 15U) | 0x7C00U);
  }

  std::uint16_t half =
      static_cast<std::uint16_t>((sign << 15U) | (static_cast<std::uint32_t>(exponent) << 10U));
  half = static_cast<std::uint16_t>(half | static_cast<std::uint16_t>(mantissa >> 13U));
  if (mantissa & 0x1000U) {
    half = static_cast<std::uint16_t>(half + 1U);
  }
  return half;
}

float halfBitsToFloat(std::uint16_t bits) {
  std::uint32_t sign = (static_cast<std::uint32_t>(bits & 0x8000U)) << 16U;
  std::uint32_t exponent = (bits >> 10U) & 0x1FU;
  std::uint32_t mantissa = bits & 0x03FFU;
  std::uint32_t value = 0;

  if (exponent == 0) {
    if (mantissa == 0) {
      value = sign;
    } else {
      exponent = 1;
      while ((mantissa & 0x0400U) == 0U) {
        mantissa <<= 1U;
        exponent--;
      }
      mantissa &= 0x03FFU;
      value = sign | ((exponent + 127U - 15U) << 23U) | (mantissa << 13U);
    }
  } else if (exponent == 0x1FU) {
    value = sign | 0x7F800000U | (mantissa << 13U);
  } else {
    value = sign | ((exponent + 127U - 15U) << 23U) | (mantissa << 13U);
  }

  float result = 0.0F;
  std::memcpy(&result, &value, sizeof(result));
  return result;
}

void writeFloat16(std::vector<std::uint8_t>& out, float value, bool littleEndian) {
  writeIntegral<std::uint16_t>(out, floatToHalfBits(value), littleEndian);
}

float readFloat16(
    std::span<const std::uint8_t> data,
    std::size_t& cursor,
    bool littleEndian,
    const char* label) {
  return halfBitsToFloat(readIntegral<std::uint16_t>(data, cursor, littleEndian, label));
}

void writeFloat32(std::vector<std::uint8_t>& out, float value, bool littleEndian) {
  std::uint32_t bits = 0;
  std::memcpy(&bits, &value, sizeof(bits));
  writeIntegral<std::uint32_t>(out, bits, littleEndian);
}

float readFloat32(
    std::span<const std::uint8_t> data,
    std::size_t& cursor,
    bool littleEndian,
    const char* label) {
  std::uint32_t bits = readIntegral<std::uint32_t>(data, cursor, littleEndian, label);
  float value = 0.0F;
  std::memcpy(&value, &bits, sizeof(value));
  return value;
}

void writeFloat64(std::vector<std::uint8_t>& out, double value, bool littleEndian) {
  std::uint64_t bits = 0;
  std::memcpy(&bits, &value, sizeof(bits));
  writeIntegral<std::uint64_t>(out, bits, littleEndian);
}

double readFloat64(
    std::span<const std::uint8_t> data,
    std::size_t& cursor,
    bool littleEndian,
    const char* label) {
  std::uint64_t bits = readIntegral<std::uint64_t>(data, cursor, littleEndian, label);
  double value = 0.0;
  std::memcpy(&value, &bits, sizeof(value));
  return value;
}

template <typename T>
T scaleToSignedRaw(double logicalValue, double scale, const char* fieldName) {
  if (scale == 0.0) {
    throw std::invalid_argument(std::string(fieldName) + ": scale must not be zero.");
  }
  double rounded = std::round(logicalValue / scale);
  if (rounded < static_cast<double>(std::numeric_limits<T>::lowest())
      || rounded > static_cast<double>(std::numeric_limits<T>::max())) {
    throw std::invalid_argument(
        std::string(fieldName) + ": scaled value is out of range for its integer base type.");
  }
  return static_cast<T>(rounded);
}

template <typename T>
T scaleToUnsignedRaw(double logicalValue, double scale, const char* fieldName) {
  if (scale == 0.0) {
    throw std::invalid_argument(std::string(fieldName) + ": scale must not be zero.");
  }
  double rounded = std::round(logicalValue / scale);
  if (rounded < 0.0 || rounded > static_cast<double>(std::numeric_limits<T>::max())) {
    throw std::invalid_argument(
        std::string(fieldName) + ": scaled value is out of range for its integer base type.");
  }
  return static_cast<T>(rounded);
}

template <typename T>
std::size_t requireCount(T value, const char* fieldName) {
  if constexpr (std::is_signed_v<T>) {
    if (value < 0) {
      throw std::invalid_argument(
          std::string("Count field ") + fieldName + " must not be negative.");
    }
  }
  using Unsigned = std::make_unsigned_t<T>;
  Unsigned count = static_cast<Unsigned>(value);
  if (count > static_cast<Unsigned>(std::numeric_limits<std::size_t>::max())) {
    throw std::invalid_argument(
        std::string("Count field ") + fieldName + " is too large for this platform.");
  }
  return static_cast<std::size_t>(count);
}

}  // namespace

bool TelemetryFrame::getStatusBitsAlarm() const {
  return (static_cast<std::uint64_t>(statusBits) & 1ULL) != 0ULL;
}

void TelemetryFrame::setStatusBitsAlarm(bool value) {
  std::uint64_t raw = static_cast<std::uint64_t>(statusBits);
  if (value) {
    raw |= 1ULL;
  } else {
    raw &= ~1ULL;
  }
  statusBits = static_cast<std::uint8_t>(raw);
}

std::uint64_t TelemetryFrame::getStatusBitsState() const {
  return (static_cast<std::uint64_t>(statusBits) >> 1) & 3ULL;
}

void TelemetryFrame::setStatusBitsState(std::uint64_t value) {
  if (value > 3ULL) {
    throw std::invalid_argument("statusBits.state is out of range for its bit segment.");
  }
  std::uint64_t raw = static_cast<std::uint64_t>(statusBits);
  raw = (raw & ~6ULL) | ((value & 3ULL) << 1);
  statusBits = static_cast<std::uint8_t>(raw);
}

std::vector<std::uint8_t> TelemetryFrame::encode() const {
  std::vector<std::uint8_t> out;
  writeIntegral<std::uint8_t>(out, this->version, false);
  writeIntegral<std::uint8_t>(out, this->statusBits, false);
  writeIntegral<std::int32_t>(out, scaleToSignedRaw<std::int32_t>(this->temperature, 0.1, "temperature"), true);
  writeIntegral<std::uint16_t>(out, scaleToUnsignedRaw<std::uint16_t>(this->voltage, 0.01, "voltage"), false);
  writeIntegral<std::int16_t>(out, scaleToSignedRaw<std::int16_t>(this->reusableTemperature, 0.01, "reusableTemperature"), false);
  writeFloat32(out, static_cast<float>(this->reusableFloat), false);
  return out;
}

TelemetryFrame TelemetryFrame::decode(std::span<const std::uint8_t> data) {
  std::size_t cursor = 0;
  TelemetryFrame value = TelemetryFrame::decodeFrom(data, cursor);
  if (cursor != data.size()) {
    throw std::invalid_argument("Extra bytes remain after decoding TelemetryFrame.");
  }
  return value;
}

TelemetryFrame TelemetryFrame::decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor) {
  TelemetryFrame value{};
  value.version = readIntegral<std::uint8_t>(data, cursor, false, "version");
  value.statusBits = readIntegral<std::uint8_t>(data, cursor, false, "statusBits");
  value.temperature = static_cast<double>(readIntegral<std::int32_t>(data, cursor, true, "temperature")) * 0.1;
  value.voltage = static_cast<double>(readIntegral<std::uint16_t>(data, cursor, false, "voltage")) * 0.01;
  value.reusableTemperature = static_cast<double>(readIntegral<std::int16_t>(data, cursor, false, "reusableTemperature")) * 0.01;
  value.reusableFloat = static_cast<double>(readFloat32(data, cursor, false, "reusableFloat"));
  return value;
}

}  // namespace numeric
}  // namespace telemetry
}  // namespace acme
