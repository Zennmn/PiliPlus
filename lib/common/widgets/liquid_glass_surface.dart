import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Preserves the full drawing area while only the 56dp capsule accepts touches.
class LiquidGlassSurface extends StatelessWidget {
  const LiquidGlassSurface({super.key, required this.controller});

  final AndroidViewController controller;

  static const capsuleHeight = 56.0;
  static const drawingHeight = capsuleHeight + 32;

  @override
  Widget build(BuildContext context) => _CapsuleTouchRegion(
    child: AndroidViewSurface(
      controller: controller,
      // Forward down immediately so Compose owns press, hold and drag timing.
      gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{
        Factory<OneSequenceGestureRecognizer>(EagerGestureRecognizer.new),
      },
      hitTestBehavior: PlatformViewHitTestBehavior.opaque,
    ),
  );
}

class _CapsuleTouchRegion extends SingleChildRenderObjectWidget {
  const _CapsuleTouchRegion({required super.child});

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderCapsuleTouchRegion();
}

class _RenderCapsuleTouchRegion extends RenderProxyBox {
  @override
  bool hitTest(BoxHitTestResult result, {required Offset position}) {
    // Match Compose's centered 56dp capsule and 40dp horizontal spacing.
    // Hit testing only: shadows and the enlarged pressed layer remain visible.
    final capsule = RRect.fromRectAndRadius(
      Rect.fromLTRB(
        40,
        (size.height - LiquidGlassSurface.capsuleHeight) / 2,
        size.width - 40,
        (size.height + LiquidGlassSurface.capsuleHeight) / 2,
      ),
      const Radius.circular(LiquidGlassSurface.capsuleHeight / 2),
    );
    return capsule.contains(position) &&
        super.hitTest(result, position: position);
  }
}
