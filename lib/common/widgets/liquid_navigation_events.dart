import 'package:flutter/services.dart';

/// Native gestures are the only messages that can trigger navigation.
void dispatchLiquidNavigationEvent(
  MethodCall call, {
  required bool active,
  required int destinationCount,
  required ValueChanged<int> onSelected,
}) {
  if (!active || call.method != 'select') return;
  final arguments = call.arguments;
  if (arguments is! Map) return;
  final index = arguments['index'];
  if (index is int && index >= 0 && index < destinationCount) {
    onSelected(index);
  }
}
