// ignore_for_file: unused_elements, unused_local_variable

void main() {
  // Basic variable declarations
  var name = 'John'; // Should show: name: String
  final age = 25; // Should show: age: int
  final age1 = 25.0; // Should show: age1: double
  final list = [1, 2, 3]; // Should show: list: List<int>

  // Enhanced collection types
  var stringList = ['a', 'b', 'c']; // Should show: stringList: List<String>
  final mixedList = [1, 'b', true]; // Should show: mixedList: List (no generic)
  var emptyList = <String>[]; // Should show: emptyList: List<String>
  final numbers = <int>[1, 2, 3]; // Should show: numbers: List<int>

  // Map and Set examples
  var stringMap = {
    'key': 'value'
  }; // Should show: stringMap: Map<String, String>
  final intSet = {1, 2, 3}; // Should show: intSet: Set<int>
  var emptyMap = <String, int>{}; // Should show: emptyMap: Map<String, int>

  // No hints shown (explicit type)
  String explicitName = "Jane";
  int explicitAge = 18;

  // Constructor calls
  final foo = Foo('bar1', bar2: 'bar2', bar3: 'bar3'); // Should show: foo: Foo
  final foobar1 = foo.bar1; // Would need PSI for proper inference
  final foobar2 = foo.bar2;
  final foobar3 = foo.bar3;

  // Pattern/destructuring binding
  final (fb1, fb2, fb3) =
      foo.toRecord(); // Should show: fb1: String, fb2: String, fb3: String?
  var (a, b) = (1, 'hello'); // Should show: a: int, b: String
  final (x, y, z) =
      (1.0, true, null); // Should show: x: double, y: bool, z: Null

  // For-each loops - CRITICAL TEST CASES
  // These test cases were specifically mentioned as broken in the PR comments
  for (var e in <int>[1, 2, 3]) {
    // Should show: int e (prefix format)
    print(e);
  }

  for (var e in 'hello'.split('')) {
    // Should show: String e (NOT int e from previous loop - no variable caching!)
    print(e);
  }

  for (var char in 'hello'.split('')) {
    // Should show: String char (was completely broken before)
    print(char);
  }

  for (var item in list) {
    // Should show: int item (was not working)
    print(item);
  }

  for (var s in ['a', 'b', 'c']) {
    // Should show: String s
    print(s);
  }

  // Iterable.generate for ranges (Dart way)
  for (var i in Iterable.generate(10, (index) => index)) {
    // Should show: i: int
    print(i);
  }

  // List.generate
  for (var num in List.generate(5, (i) => i * 2)) {
    // Should show: num: int
    print(num);
  }

  // Underscore variables (should be suppressed)
  var _ = 'ignored'; // No hint (suppressed)
  final __ = 42; // No hint (suppressed)
  var temp = 'temporary'; // No hint if in blacklist

  // Dynamic type (should be suppressed if enabled)
  var dynamic_var = getValue(); // No hint if suppressDynamic enabled

  // Method calls with known return types
  var stringLength = 'hello'.length; // Should show: stringLength: int
  final stringRep = 42.toString(); // Should show: stringRep: String
  var doubleVal = 5.toDouble(); // Should show: doubleVal: double
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
