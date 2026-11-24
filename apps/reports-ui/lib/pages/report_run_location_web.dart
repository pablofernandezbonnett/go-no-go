import 'dart:js_interop';

@JS('window.addEventListener')
external void _addEventListener(JSString type, JSFunction listener);

@JS('window.removeEventListener')
external void _removeEventListener(JSString type, JSFunction listener);

void Function() watchReportLocation(void Function() onChange) {
  final event = 'popstate'.toJS;
  final listener = onChange.toJS;
  _addEventListener(event, listener);
  return () => _removeEventListener(event, listener);
}
