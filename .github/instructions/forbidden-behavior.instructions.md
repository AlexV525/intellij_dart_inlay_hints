Here are some forbidden behaviors when implementing features.

1. Do not use `PsiRecursiveVisitor` to traverse the AST. It is too slow and will cause performance issues.
   Instead, use `PsiElementVisitor` and manually traverse the AST.
2. Do not use `PsiTreeUtil.findChildrenOfType` to find children. It is too slow and will cause performance issues.
   Instead, use `PsiElement.getChildren` and manually filter the children.
3. Do not use `PsiTreeUtil.findChildrenOfAnyType` to find children. It is too slow and will cause performance issues.
   Instead, use `PsiElement.getChildren` and manually filter the children.
4. Do not match only with the example, think broader and deeper. For example, if the example is `foo.toRecord()`, 
   you should also match `bar.anyMethods(withAnyArgs)`.
5. Do not hard-code patterns unless I told you to do so.
