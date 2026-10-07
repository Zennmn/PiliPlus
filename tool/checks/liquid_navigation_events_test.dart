import 'package:PiliPlus/common/widgets/liquid_navigation_events.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final selected = <int>[];
  void receive(MethodCall call, {bool active = true, int count = 3}) =>
      dispatchLiquidNavigationEvent(
        call,
        active: active,
        destinationCount: count,
        onSelected: selected.add,
      );

  setUp(selected.clear);

  test('click and drag each emit one navigation action', () {
    receive(const MethodCall('select', {'index': 1, 'gesture': 'tap'}));
    receive(const MethodCall('select', {'index': 2, 'gesture': 'drag'}));
    expect(selected, [1, 2]);
  });

  test('repeated selected-tab taps remain separate refresh actions', () {
    receive(const MethodCall('select', {'index': 0, 'gesture': 'tap'}));
    receive(const MethodCall('select', {'index': 0, 'gesture': 'tap'}));
    expect(selected, [0, 0]);
  });

  test('state updates never echo navigation', () {
    receive(const MethodCall('update', {'selectedIndex': 2}));
    expect(selected, isEmpty);
  });

  test('hidden or background views cannot select pages', () {
    receive(const MethodCall('select', {'index': 1}), active: false);
    expect(selected, isEmpty);
  });

  test('reconfigured destinations reject stale indices', () {
    receive(const MethodCall('select', {'index': 2}), count: 2);
    receive(const MethodCall('select', {'index': -1}));
    receive(const MethodCall('select', {'index': 1}), count: 2);
    expect(selected, [1]);
  });
}
