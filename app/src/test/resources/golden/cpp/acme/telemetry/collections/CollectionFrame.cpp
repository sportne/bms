#include "acme/telemetry/collections/CollectionFrame.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <string>
#include <type_traits>

namespace acme {
namespace telemetry {
namespace collections {

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

std::vector<std::uint8_t> CollectionFrame::encode() const {
  std::vector<std::uint8_t> out;
  writeIntegral<std::uint16_t>(out, this->count, false);
  (void)"count";
  writeIntegral<std::uint8_t>(out, this->blobCount, false);
  for (std::size_t indexSamples = 0; indexSamples < 4; indexSamples++) {
    auto samplesItem = this->samples[indexSamples];
  writeIntegral<std::uint8_t>(out, samplesItem, false);
  }
  std::size_t expectedEventsCount = requireCount(this->count, "count");
  if (this->events.size() != expectedEventsCount) {
    throw std::invalid_argument("events length must match count field count.");
  }
  for (std::size_t indexEvents = 0; indexEvents < this->events.size(); indexEvents++) {
    auto eventsItem = this->events[indexEvents];
  writeIntegral<std::uint8_t>(out, eventsItem, false);
  }
  out.insert(out.end(), this->hash.begin(), this->hash.end());
  std::size_t expectedPayloadCount = requireCount(this->blobCount, "blobCount");
  if (this->payload.size() != expectedPayloadCount) {
    throw std::invalid_argument("payload length must match count field blobCount.");
  }
  out.insert(out.end(), this->payload.begin(), this->payload.end());
  for (std::size_t indexPathData = 0; indexPathData < this->pathData.size(); indexPathData++) {
    auto pathDataItem = this->pathData[indexPathData];
  writeIntegral<std::uint8_t>(out, pathDataItem, false);
  }
  writeIntegral<std::uint8_t>(out, static_cast<std::uint8_t>(0ULL), false);
  for (std::size_t indexBytePair = 0; indexBytePair < 2; indexBytePair++) {
    auto bytePairItem = this->reusableArrayField[indexBytePair];
  writeIntegral<std::uint8_t>(out, bytePairItem, false);
  }
  std::size_t expectedReusableVectorFieldCount = requireCount(this->count, "count");
  if (this->reusableVectorField.size() != expectedReusableVectorFieldCount) {
    throw std::invalid_argument("reusableVectorField length must match count field count.");
  }
  for (std::size_t indexReusableVectorField = 0; indexReusableVectorField < this->reusableVectorField.size(); indexReusableVectorField++) {
    auto reusableVectorFieldItem = this->reusableVectorField[indexReusableVectorField];
  writeIntegral<std::uint8_t>(out, reusableVectorFieldItem, false);
  }
  out.insert(out.end(), this->reusableBlobArrayField.begin(), this->reusableBlobArrayField.end());
  out.insert(out.end(), this->reusableBlobVectorField.begin(), this->reusableBlobVectorField.end());
  writeIntegral<std::uint8_t>(out, static_cast<std::uint8_t>(0ULL), false);
  return out;
}

CollectionFrame CollectionFrame::decode(std::span<const std::uint8_t> data) {
  std::size_t cursor = 0;
  CollectionFrame value = CollectionFrame::decodeFrom(data, cursor);
  if (cursor != data.size()) {
    throw std::invalid_argument("Extra bytes remain after decoding CollectionFrame.");
  }
  return value;
}

CollectionFrame CollectionFrame::decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor) {
  CollectionFrame value{};
  value.count = readIntegral<std::uint16_t>(data, cursor, false, "count");
  value.blobCount = readIntegral<std::uint8_t>(data, cursor, false, "blobCount");
  for (std::size_t indexSamples = 0; indexSamples < 4; indexSamples++) {
    std::uint8_t samplesItem = readIntegral<std::uint8_t>(data, cursor, false, "samples_item");
    value.samples[indexSamples] = samplesItem;
  }
  {
    std::size_t expectedEventsCount = requireCount(value.count, "count");
    value.events.clear();
    value.events.reserve(expectedEventsCount);
    for (std::size_t indexEvents = 0; indexEvents < expectedEventsCount; indexEvents++) {
    std::uint8_t eventsItem = readIntegral<std::uint8_t>(data, cursor, false, "events_item");
      value.events.push_back(eventsItem);
    }
  }
  requireReadable(data, cursor, value.hash.size(), "hash");
  std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), value.hash.size(), value.hash.begin());
  cursor += value.hash.size();
  {
    std::size_t expectedPayloadCount = requireCount(value.blobCount, "blobCount");
    requireReadable(data, cursor, expectedPayloadCount, "payload");
    value.payload.assign(
        data.begin() + static_cast<std::ptrdiff_t>(cursor),
        data.begin() + static_cast<std::ptrdiff_t>(cursor + expectedPayloadCount));
    cursor += expectedPayloadCount;
  }
  value.pathData.clear();
  while (true) {
    std::uint8_t pathDataItem = readIntegral<std::uint8_t>(data, cursor, false, "pathData_item");
    if (pathDataItem == static_cast<std::uint8_t>(0ULL)) {
      break;
    }
    value.pathData.push_back(pathDataItem);
  }
  for (std::size_t indexBytePair = 0; indexBytePair < 2; indexBytePair++) {
    std::uint8_t bytePairItem = readIntegral<std::uint8_t>(data, cursor, false, "BytePair_item");
    value.reusableArrayField[indexBytePair] = bytePairItem;
  }
  {
    std::size_t expectedReusableVectorFieldCount = requireCount(value.count, "count");
    value.reusableVectorField.clear();
    value.reusableVectorField.reserve(expectedReusableVectorFieldCount);
    for (std::size_t indexReusableVectorField = 0; indexReusableVectorField < expectedReusableVectorFieldCount; indexReusableVectorField++) {
    std::uint8_t reusableVectorFieldItem = readIntegral<std::uint8_t>(data, cursor, false, "reusableVectorField_item");
      value.reusableVectorField.push_back(reusableVectorFieldItem);
    }
  }
  requireReadable(data, cursor, value.reusableBlobArrayField.size(), "reusableBlobArrayField");
  std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), value.reusableBlobArrayField.size(), value.reusableBlobArrayField.begin());
  cursor += value.reusableBlobArrayField.size();
  value.reusableBlobVectorField.clear();
  while (true) {
    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, "reusableBlobVectorField_item");
    if (nextByte == static_cast<std::uint8_t>(0ULL)) {
      break;
    }
    value.reusableBlobVectorField.push_back(nextByte);
  }
  return value;
}

}  // namespace collections
}  // namespace telemetry
}  // namespace acme
