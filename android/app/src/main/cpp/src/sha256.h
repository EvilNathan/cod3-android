/**
 * Minimal SHA-256 (FIPS 180-4) for build-time verification logging.
 * Self-contained, no dependencies; used to log the default.xex digest read
 * through the ReXGlue VFS (folder or disc image) at boot.
 */
#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

namespace project1944 {

class Sha256 {
 public:
  Sha256() { reset(); }

  void reset() {
    h_[0] = 0x6a09e667;
    h_[1] = 0xbb67ae85;
    h_[2] = 0x3c6ef372;
    h_[3] = 0xa54ff53a;
    h_[4] = 0x510e527f;
    h_[5] = 0x9b05688c;
    h_[6] = 0x1f83d9ab;
    h_[7] = 0x5be0cd19;
    total_ = 0;
    buffer_len_ = 0;
  }

  void update(const void* data, size_t size) {
    const auto* bytes = static_cast<const uint8_t*>(data);
    total_ += size;
    while (size > 0) {
      const size_t take = (sizeof(buffer_) - buffer_len_) < size
                              ? (sizeof(buffer_) - buffer_len_)
                              : size;
      std::memcpy(buffer_ + buffer_len_, bytes, take);
      buffer_len_ += take;
      bytes += take;
      size -= take;
      if (buffer_len_ == sizeof(buffer_)) {
        transform(buffer_);
        buffer_len_ = 0;
      }
    }
  }

  std::string hexdigest() {
    finish();
    char out[65];
    for (size_t i = 0; i < 8; ++i) {
      std::snprintf(out + i * 8, 9, "%08x", h_[i]);
    }
    return std::string(out, 64);
  }

 private:
  static uint32_t rotr(uint32_t value, uint32_t bits) {
    return (value >> bits) | (value << (32 - bits));
  }

  void transform(const uint8_t* chunk) {
    static const uint32_t k[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
        0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
        0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
        0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
        0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

    uint32_t w[64];
    for (size_t i = 0; i < 16; ++i) {
      w[i] = (static_cast<uint32_t>(chunk[i * 4]) << 24) |
             (static_cast<uint32_t>(chunk[i * 4 + 1]) << 16) |
             (static_cast<uint32_t>(chunk[i * 4 + 2]) << 8) |
             static_cast<uint32_t>(chunk[i * 4 + 3]);
    }
    for (size_t i = 16; i < 64; ++i) {
      const uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
      const uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
      w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }

    uint32_t a = h_[0], b = h_[1], c = h_[2], d = h_[3];
    uint32_t e = h_[4], f = h_[5], g = h_[6], hh = h_[7];
    for (size_t i = 0; i < 64; ++i) {
      const uint32_t s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const uint32_t ch = (e & f) ^ (~e & g);
      const uint32_t temp1 = hh + s1 + ch + k[i] + w[i];
      const uint32_t s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
      const uint32_t temp2 = s0 + maj;
      hh = g;
      g = f;
      f = e;
      e = d + temp1;
      d = c;
      c = b;
      b = a;
      a = temp1 + temp2;
    }
    h_[0] += a;
    h_[1] += b;
    h_[2] += c;
    h_[3] += d;
    h_[4] += e;
    h_[5] += f;
    h_[6] += g;
    h_[7] += hh;
  }

  void finish() {
    const uint64_t bit_len = total_ * 8;
    const uint8_t pad_byte = 0x80;
    update(&pad_byte, 1);
    static const uint8_t zero = 0;
    while (buffer_len_ != 56) {
      update(&zero, 1);
    }
    uint8_t len_bytes[8];
    for (size_t i = 0; i < 8; ++i) {
      len_bytes[i] = static_cast<uint8_t>(bit_len >> (56 - i * 8));
    }
    update(len_bytes, sizeof(len_bytes));
  }

  std::array<uint32_t, 8> h_{};
  uint8_t buffer_[64];
  size_t buffer_len_ = 0;
  uint64_t total_ = 0;
};

}  // namespace project1944
