import 'dart:io';

const _httpScheme = 'http';
const _httpsScheme = 'https';
const _maximumRawTextLength = 100000;
final _unsafeControlCharacters = RegExp(r'[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]');
final _ipv4LiteralPattern = RegExp(r'^\d{1,3}(?:\.\d{1,3}){3}$');

class EvaluationInputSafety {
  const EvaluationInputSafety();

  String validateUrl(String rawUrl) {
    final trimmed = rawUrl.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('jobUrl is required in URL mode.');
    }

    final uri = Uri.tryParse(trimmed);
    if (uri == null || !uri.hasScheme) {
      throw const FormatException('jobUrl must be a valid URL.');
    }
    if (uri.scheme != _httpScheme && uri.scheme != _httpsScheme) {
      throw const FormatException('Only public http/https URLs are allowed.');
    }
    if (uri.userInfo.isNotEmpty) {
      throw const FormatException('URLs with embedded credentials are not allowed.');
    }

    final host = uri.host.trim().toLowerCase();
    if (host.isEmpty) {
      throw const FormatException('jobUrl host is missing or invalid.');
    }
    if (_isBlockedHostname(host) || _isPrivateOrLocalIpLiteral(host)) {
      throw const FormatException('Local or private network URLs are not allowed.');
    }
    return trimmed;
  }

  String sanitizeRawText(String rawText) {
    final sanitized = rawText.replaceAll(_unsafeControlCharacters, '').trim();
    if (sanitized.isEmpty) {
      throw const FormatException('rawText is required in raw_text mode.');
    }
    if (sanitized.length > _maximumRawTextLength) {
      throw const FormatException('rawText is too large for ad-hoc evaluation.');
    }
    return sanitized;
  }

  String sanitizeOverride(String rawValue) {
    return rawValue.replaceAll(_unsafeControlCharacters, '').trim();
  }

  bool _isBlockedHostname(String host) {
    return host == 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal');
  }

  bool _isPrivateOrLocalIpLiteral(String host) {
    if (!_ipv4LiteralPattern.hasMatch(host) && !host.contains(':')) {
      return false;
    }

    final address = InternetAddress.tryParse(host);
    if (address == null) {
      return false;
    }
    if (address.isLoopback || address.isLinkLocal || address.type == InternetAddressType.unix) {
      return true;
    }
    if (address.type == InternetAddressType.IPv4) {
      final octets = host.split('.').map(int.parse).toList(growable: false);
      final first = octets[0];
      final second = octets[1];
      return first == 0 ||
          first == 10 ||
          first == 127 ||
          (first == 169 && second == 254) ||
          (first == 172 && second >= 16 && second <= 31) ||
          (first == 192 && second == 168) ||
          (first == 100 && second >= 64 && second <= 127) ||
          (first == 198 && (second == 18 || second == 19));
    }

    final normalized = host.toLowerCase();
    return normalized == '::1' || normalized.startsWith('fc') || normalized.startsWith('fd');
  }
}
