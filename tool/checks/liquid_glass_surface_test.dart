import 'package:PiliPlus/common/widgets/liquid_glass_surface.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

class _RecordingAndroidView implements AndroidViewController {
  final events = <PointerEvent>[];

  @override
  int get viewId => 42;
  @override
  bool get isCreated => true;
  @override
  bool get requiresViewComposition => true;
  @override
  late PointTransformer pointTransformer;

  @override
  Future<void> dispatchPointerEvent(PointerEvent event) async =>
      events.add(event);

  @override
  void removeOnPlatformViewCreatedListener(
    PlatformViewCreatedCallback listener,
  ) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  late _RecordingAndroidView controller;
  var backgroundTaps = 0;
  var ancestorDrags = 0;

  setUp(() {
    controller = _RecordingAndroidView();
    backgroundTaps = 0;
    ancestorDrags = 0;
  });

  Future<void> mount(WidgetTester tester) => tester.pumpWidget(
    Directionality(
      textDirection: TextDirection.ltr,
      child: Align(
        alignment: Alignment.topLeft,
        child: SizedBox(
          width: 360,
          height: LiquidGlassSurface.drawingHeight,
          child: GestureDetector(
            onHorizontalDragUpdate: (_) => ancestorDrags++,
            child: Stack(
              fit: StackFit.expand,
              children: [
                GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => backgroundTaps++,
                  child: const SizedBox.expand(),
                ),
                LiquidGlassSurface(controller: controller),
              ],
            ),
          ),
        ),
      ),
    ),
  );

  testWidgets('capsule delivers down immediately and blocks content below', (
    tester,
  ) async {
    await mount(tester);
    final gesture = await tester.startGesture(const Offset(180, 44));
    expect(controller.events.whereType<PointerDownEvent>(), hasLength(1));
    await gesture.up();
    expect(controller.events.whereType<PointerUpEvent>(), hasLength(1));
    expect(backgroundTaps, 0);
  });

  testWidgets(
    'holding and dragging keeps the whole native stream outside the capsule',
    (tester) async {
      await mount(tester);
      final gesture = await tester.startGesture(const Offset(90, 44));
      await tester.pump(const Duration(milliseconds: 800));
      expect(controller.events.whereType<PointerDownEvent>(), hasLength(1));
      expect(controller.events.whereType<PointerCancelEvent>(), isEmpty);
      await gesture.moveTo(const Offset(270, 44));
      await gesture.moveTo(const Offset(10, 130));
      await gesture.up();
      expect(controller.events.whereType<PointerMoveEvent>(), hasLength(2));
      expect(controller.events.whereType<PointerUpEvent>(), hasLength(1));
      expect(ancestorDrags, 0);
      expect(backgroundTaps, 0);
    },
  );

  testWidgets('outside spacing and rounded corners still reach the page', (
    tester,
  ) async {
    await mount(tester);
    for (final position in [
      const Offset(8, 48),
      const Offset(38, 44),
      const Offset(180, 8),
      const Offset(180, 74),
      const Offset(180, 80),
      const Offset(41, 17),
      const Offset(319, 71),
    ]) {
      await tester.tapAt(position);
    }
    expect(backgroundTaps, 7);
    expect(controller.events, isEmpty);
  });

  testWidgets('both finger streams reach native through owner handover', (
    tester,
  ) async {
    await mount(tester);
    final first = await tester.startGesture(const Offset(90, 44), pointer: 1);
    final second = await tester.startGesture(const Offset(270, 44), pointer: 2);
    await second.moveTo(const Offset(180, 44));
    await first.up();
    await second.moveTo(const Offset(90, 44));
    await second.up();
    expect(controller.events.whereType<PointerDownEvent>(), hasLength(2));
    expect(controller.events.whereType<PointerMoveEvent>(), hasLength(2));
    expect(controller.events.whereType<PointerUpEvent>(), hasLength(2));
    expect(controller.events.whereType<PointerCancelEvent>(), isEmpty);
    expect(ancestorDrags, 0);
    expect(backgroundTaps, 0);
  });

  testWidgets('cancellation is delivered to the native gesture owner', (
    tester,
  ) async {
    await mount(tester);
    final gesture = await tester.startGesture(const Offset(90, 44));
    await gesture.moveTo(const Offset(270, 44));
    await gesture.cancel();
    expect(controller.events.whereType<PointerCancelEvent>(), hasLength(1));
    expect(controller.events.whereType<PointerUpEvent>(), isEmpty);
    expect(backgroundTaps, 0);
  });
}
