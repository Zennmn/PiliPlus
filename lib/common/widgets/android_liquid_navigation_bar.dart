import 'dart:async';

import 'package:PiliPlus/common/widgets/liquid_glass_surface.dart';
import 'package:PiliPlus/common/widgets/route_aware_mixin.dart';
import 'package:PiliPlus/common/widgets/liquid_navigation_events.dart';
import 'package:PiliPlus/models/common/dynamic/dynamic_badge_mode.dart';
import 'package:PiliPlus/models/common/nav_bar_config.dart';
import 'package:PiliPlus/utils/storage_pref.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

/// Android-only host for the original Compose LiquidBottomTabs and Backdrop renderer.
class AndroidLiquidNavigationBar extends StatefulWidget {
  const AndroidLiquidNavigationBar({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onDestinationSelected,
    required this.dynamicCount,
    required this.dynamicBadgeMode,
    this.visible = true,
  });

  final List<NavigationBarType> destinations;
  final int selectedIndex;
  final ValueChanged<int> onDestinationSelected;
  final int dynamicCount;
  final DynamicBadgeMode dynamicBadgeMode;
  final bool visible;

  @override
  State<AndroidLiquidNavigationBar> createState() =>
      _AndroidLiquidNavigationBarState();
}

class _AndroidLiquidNavigationBarState extends State<AndroidLiquidNavigationBar>
    with WidgetsBindingObserver, RouteAware, RouteAwareMixin {
  MethodChannel? _channel;
  bool _routeActive = true;
  bool _resumed = true;
  bool _updateScheduled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _resumed =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
  }

  Map<String, Object?> _state() => {
    'selectedIndex': widget.selectedIndex,
    'light': Theme.of(context).brightness == Brightness.light,
    'badgeColor': Theme.of(context).colorScheme.error.toARGB32(),
    'badgeTextColor': Theme.of(context).colorScheme.onError.toARGB32(),
    'uiScale': Pref.uiScale,
    'textScale': MediaQuery.textScalerOf(context).scale(1),
    'active': _routeActive && _resumed && widget.visible,
    'tabs': widget.destinations.map((tab) {
      String? badge;
      if (tab == NavigationBarType.dynamics &&
          widget.dynamicCount > 0 &&
          widget.dynamicBadgeMode != DynamicBadgeMode.hidden) {
        badge = widget.dynamicBadgeMode == DynamicBadgeMode.number
            ? widget.dynamicCount.toString()
            : '';
      }
      return {
        'id': tab.name,
        'label': tab.label,
        'icon': tab.icon.icon!.codePoint,
        'selectedIcon': tab.selectIcon.icon!.codePoint,
        'font': tab.icon.icon!.fontFamily!,
        'badge': badge,
      };
    }).toList(),
  };

  void _scheduleUpdate() {
    if (_updateScheduled || _channel == null) return;
    _updateScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _updateScheduled = false;
      if (mounted && _channel != null) {
        unawaited(_channel!.invokeMethod<void>('update', _state()));
      }
    });
  }

  void _onCreated(int id) {
    _channel = MethodChannel('piliplus/liquid_glass/$id');
    _channel!.setMethodCallHandler((call) async {
      dispatchLiquidNavigationEvent(
        call,
        active: _routeActive && _resumed && widget.visible,
        destinationCount: widget.destinations.length,
        onSelected: widget.onDestinationSelected,
      );
    });
    _scheduleUpdate();
  }

  @override
  void didUpdateWidget(covariant AndroidLiquidNavigationBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    _scheduleUpdate();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _scheduleUpdate();
  }

  @override
  void didPushNext() {
    _routeActive = false;
    _scheduleUpdate();
  }

  @override
  void didPopNext() {
    _routeActive = true;
    _scheduleUpdate();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _resumed = state == AppLifecycleState.resumed;
    _scheduleUpdate();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _channel?.setMethodCallHandler(null);
    _channel = null;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _scheduleUpdate();
    // Reserve 16dp on each side of the 56dp capsule for its press animation.
    final bottom = MediaQuery.viewPaddingOf(context).bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: LiquidGlassSurface.drawingHeight,
        child: PlatformViewLink(
          viewType: 'piliplus/liquid_glass',
          surfaceFactory: (context, controller) => LiquidGlassSurface(
            controller: controller as AndroidViewController,
          ),
          onCreatePlatformView: (params) {
            final controller =
                PlatformViewsService.initExpensiveAndroidView(
                    id: params.id,
                    viewType: 'piliplus/liquid_glass',
                    layoutDirection: Directionality.of(context),
                    creationParams: _state(),
                    creationParamsCodec: const StandardMessageCodec(),
                    onFocus: () => params.onFocusChanged(true),
                  )
                  ..addOnPlatformViewCreatedListener(
                    params.onPlatformViewCreated,
                  )
                  ..addOnPlatformViewCreatedListener(_onCreated);
            unawaited(controller.create());
            return controller;
          },
        ),
      ),
    );
  }
}
