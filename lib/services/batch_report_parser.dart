import '../models/batch_item_payload.dart';

List<BatchItemPayload> batchItemsFromDecodedJson(Object? decodedJson) {
  final root = _asMap(decodedJson);
  final rawItems = root['items'];
  if (rawItems is! List) {
    return const <BatchItemPayload>[];
  }
  return rawItems.map((entry) => BatchItemPayload.fromJson(_asMap(entry))).toList();
}

Map<String, dynamic> _asMap(Object? value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    final mapped = <String, dynamic>{};
    for (final entry in value.entries) {
      mapped[entry.key.toString()] = entry.value;
    }
    return mapped;
  }
  return const <String, dynamic>{};
}
