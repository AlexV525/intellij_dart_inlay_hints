// ignore_for_file: unused_elements, unused_local_variable

void main() {
  var name = 'John';
  final age = 25;
  final age1 = 25.0;
  final list = [1, 2, 3];

  // No hints shown (explicit type)
  String explicitName = "Jane";
  int explicitAge = 18;

  final foo = Foo('bar1', bar2: 'bar2', bar3: 'bar3');
  final foobar1 = foo.bar1;
  final foobar2 = foo.bar2;
  final foobar3 = foo.bar3;
  
  // Pattern/destructuring binding
  final (fb1, fb2, fb3) = foo.toRecord();
  
  // For-each loops
  for (var e in <int>[1,2,3]) {
    print(e);
  }
  
  for (var s in ['a', 'b', 'c']) {
    print(s);
  }
  
  for (var item in list) {
    print(item);
  }
  
  // Underscore variables (should be suppressed)
  var _ = 'ignored';
  final __ = 42;
  
  // Dynamic type (should be suppressed if enabled)
  var dynamic_var = getValue();
}

dynamic getValue() => 'hello';

class Foo {
  const Foo(
    this.bar1, {
    required this.bar2,
    this.bar3,
  });

  final String bar1;
  final String bar2;
  final String? bar3;

  (String, String, String?) toRecord() => (bar1, bar2, bar3);
}
