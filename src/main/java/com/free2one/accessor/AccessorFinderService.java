package com.free2one.accessor;

import com.free2one.accessor.meta.ClassMetadata;
import com.free2one.accessor.meta.MethodMetaDataRepository;
import com.free2one.accessor.method.AccessorMethod;
import com.free2one.accessor.settings.AccessorSettings;
import com.free2one.accessor.util.AnnotationSearchUtil;
import com.intellij.openapi.application.ReadAction;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AccessorFinderService {

    public <T extends PhpTypedElement> Map<String, Method> findSetterMethods(T phpTypedElement) {
        Map<String, Method> accessMethods = new HashMap<>();
        PhpType pendingType = phpTypedElement.getType().isComplete() ? phpTypedElement.getType() : phpTypedElement.getGlobalType();
        for (String type : pendingType.getTypes()) {
            if (PhpType.isPrimitiveType(type)) {
                return accessMethods;
            }
        }

        for (String classname : pendingType.getTypes()) {
            Collection<PhpClass> phpNamedElements = PhpIndex.getInstance(phpTypedElement.getProject()).getClassesByFQN(classname);
            for (PhpClass phpClass : phpNamedElements) {
                phpClass.getMethods().stream()
                        .filter(method -> method.getName().startsWith("set"))
                        .forEach(method -> accessMethods.put(method.getName(), method));
            }
        }

        return accessMethods;
    }


    public boolean isAccessor(MethodReference reference) {
        PhpType sourceElementType = reference.getType();
        PhpType pendingType = sourceElementType.isComplete() ? sourceElementType : reference.getGlobalType();

        MethodMetaDataRepository methodMetaDataRepository = new MethodMetaDataRepository(reference.getProject());
        AccessorSettings settings = reference.getProject().getService(AccessorSettings.class);
        for (String classname : pendingType.getTypes()) {
            Collection<PhpClass> phpNamedElements = PhpIndex.getInstance(reference.getProject()).getClassesByFQN(classname);
            for (PhpClass phpClass : phpNamedElements) {
                if (settings.containSettingDirectories(phpClass.getContainingFile().getVirtualFile().getPath())) {
                    continue;
                }

                if (!AnnotationSearchUtil.isAnnotatedWith(phpClass, PhpAccessorClassnames.Data)) {
                    continue;
                }

                ClassMetadata classMetadata = ReadAction.compute(() -> methodMetaDataRepository.getFromClassname(phpClass.getFQN()));
                if (classMetadata == null) {
                    continue;
                }

                for (AccessorMethod method : classMetadata.getMethods()) {
                    if (method.getMethodName().equals(reference.getName())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
