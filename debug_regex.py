import re

# Test the for-each pattern
text = "for (var char in 'hello'.split('')) {}"
pattern = r"for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)"
match = re.search(pattern, text)

if match:
    print(f"Full match: '{match.group(0)}'")
    print(f"Keyword: '{match.group(1)}'")
    print(f"Variable: '{match.group(2)}'")
    print(f"Iterable: '{match.group(3)}'")
else:
    print("No match found")

# Test the split pattern
iterable_text = "'hello'.split('')"
split_pattern = r".+\.split\(.*\)"
if re.match(split_pattern, iterable_text):
    print(f"Split pattern matches: '{iterable_text}' -> should return String")
else:
    print(f"Split pattern doesn't match: '{iterable_text}'")
