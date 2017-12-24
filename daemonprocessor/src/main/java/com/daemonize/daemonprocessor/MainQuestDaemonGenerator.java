package com.daemonize.daemonprocessor;


import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;


public class MainQuestDaemonGenerator extends BaseDaemonGenerator implements DaemonGenerator {


    private Set<String> overloadedPrototypeMethods = new TreeSet<>();

    private String currentMainQuestName = "";

    {
        QUEST_TYPE_NAME = "MainQuest";
        daemonPackage = DAEMON_ENGINE_IMPL_PACKAGE + ".mainquestdaemon";
        daemonEngineSimpleName = "MainQuestDaemonEngine";
    }


    public MainQuestDaemonGenerator(TypeElement classElement) {
        this(classElement, classElement.getAnnotation(Daemonize.class).eager());
    }

    public MainQuestDaemonGenerator(TypeElement classElement, boolean eager) {
        super(classElement);
        if(eager) {
            daemonEngineSimpleName = "IdleMainQuestDaemonEngine";
        }
    }

    public TypeSpec generateDaemon(List<ExecutableElement> publicPrototypeMethods) {

        TypeSpec.Builder daemonClassBuilder = TypeSpec.classBuilder(daemonSimpleName)
                .addModifiers(
                        Modifier.PUBLIC,
                        Modifier.FINAL
                ).addSuperinterface(daemonInterface);

        daemonClassBuilder = addTypeParameters(classElement, daemonClassBuilder);

        Map<TypeSpec, MethodSpec> mainQuestsAndApiMethods = new LinkedHashMap<>();

        for (ExecutableElement method : publicPrototypeMethods) {
            mainQuestsAndApiMethods.put(createMainQuest(method), createApiMethod(method));
        }

        //private fields for DaemonEngine and prototype
        FieldSpec prototype = FieldSpec.builder(
                ClassName.get(classElement.asType()),
                PROTOYPE_STRING
        ).addModifiers(Modifier.PRIVATE).build();

        ClassName daemonEngineClass = ClassName.get(
                daemonPackage,
                daemonEngineSimpleName
        );

        FieldSpec daemonEngine = FieldSpec.builder(
                daemonEngineClass,
                DAEMON_ENGINE_STRING
        ).addModifiers(Modifier.PRIVATE).initializer(
                "new $N().setName(this.getClass().getSimpleName())",
                daemonEngineSimpleName
        ).build();

        daemonClassBuilder.addField(prototype);
        daemonClassBuilder.addField(daemonEngine);

        //daemon construct
        MethodSpec daemonConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(classElement.asType()), PROTOYPE_STRING)
                .addStatement("this.$N = $N", PROTOYPE_STRING, PROTOYPE_STRING)
                .build();

        daemonClassBuilder.addMethod(daemonConstructor);


        for (Map.Entry<TypeSpec, MethodSpec> entry : mainQuestsAndApiMethods.entrySet()) {
            daemonClassBuilder.addMethod(entry.getValue());
            daemonClassBuilder.addType(entry.getKey());
        }

        List<MethodSpec> daemonApiMethods = generateDaemonApiMethods();

        for (MethodSpec apiMethod : daemonApiMethods) {
            daemonClassBuilder.addMethod(apiMethod);
        }

        return daemonClassBuilder.build();
    }


    public TypeSpec createMainQuest(ExecutableElement prototypeMethod){

        PrototypeMethodData prototypeMethodData = new PrototypeMethodData(prototypeMethod);

        TypeName mainQuestOfRet = ParameterizedTypeName.get(
                ClassName.get(QUEST_PACKAGE, QUEST_TYPE_NAME),
                prototypeMethodData.getMethodRetTypeName()
        );

        String mainQuestName = Character.valueOf(
                prototypeMethodData.getMethodName().charAt(0)
        ).toString().toUpperCase() + prototypeMethodData.getMethodName().substring(1);

        //check for overloaded methods
        while (overloadedPrototypeMethods.contains(mainQuestName)) {
            mainQuestName += "I";
        }
        currentMainQuestName = mainQuestName;
        overloadedPrototypeMethods.add(mainQuestName);

        TypeSpec.Builder mainQuestBuilder = TypeSpec.classBuilder(
                mainQuestName + QUEST_TYPE_NAME
        ).superclass(mainQuestOfRet).addModifiers(Modifier.PRIVATE, Modifier.FINAL);

        mainQuestBuilder = addTypeParameters(prototypeMethod, mainQuestBuilder);

        //MainQuest construct
        MethodSpec.Builder mainQuestConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE);

        //add parameters
        for (Pair<TypeName, String> parameter : prototypeMethodData.getParameters()){
            mainQuestBuilder.addField(parameter.getFirst(), parameter.getSecond(),Modifier.PRIVATE);
            mainQuestConstructorBuilder.addParameter(parameter.getFirst(), parameter.getSecond());
        }

        if(!prototypeMethodData.isVoid()) {
            mainQuestConstructorBuilder.addParameter(
                    prototypeMethodData.getClosureOfRet(),
                    "closure"
            );
            mainQuestConstructorBuilder.addStatement("super(closure)");
        } else {
            mainQuestConstructorBuilder.addStatement("setVoid()");
        }

        for (Pair<TypeName, String> parameter : prototypeMethodData.getParameters()){
            mainQuestConstructorBuilder.addStatement("this.$N = $N", parameter.getSecond(), parameter.getSecond());
        }

        mainQuestConstructorBuilder.addStatement(
                "this.description = \"$N\"",
                prototypeMethodData.getMethodName()
        );

        MethodSpec pursue = createPursue(
                prototypeMethodData.getMethodName(),
                prototypeMethodData.getMethodRetTypeName(),
                prototypeMethodData.getArguments(),
                prototypeMethodData.isVoid(),
                prototypeMethod.getModifiers().contains(Modifier.STATIC)
        );

        MethodSpec mainQuestConstructor = mainQuestConstructorBuilder.build();

        mainQuestBuilder.addMethod(mainQuestConstructor);
        mainQuestBuilder.addMethod(pursue);

        return mainQuestBuilder.build();
    }

    public MethodSpec createApiMethod(ExecutableElement prototypeMethod) {

        PrototypeMethodData prototypeMethodData = new PrototypeMethodData(prototypeMethod);

        MethodSpec.Builder apiMethodBuilder = MethodSpec.methodBuilder(prototypeMethodData.getMethodName())
                .returns(void.class)
                .addModifiers(Modifier.PUBLIC);

        apiMethodBuilder = addTypeParameters(prototypeMethod, apiMethodBuilder);

        for (Pair<TypeName, String> field : prototypeMethodData.getParameters()) {
            apiMethodBuilder.addParameter(field.getFirst(), field.getSecond());
        }

        if (!prototypeMethodData.isVoid()) {
            apiMethodBuilder.addParameter(prototypeMethodData.getClosureOfRet(),"closure");
            apiMethodBuilder.addStatement(
                    DAEMON_ENGINE_STRING + ".pursueQuest(new "
                            + currentMainQuestName + "MainQuest("
                            + (prototypeMethodData.getArguments().isEmpty() ? "" :  prototypeMethodData.getArguments() + ", ")
                            + "closure))"
            );
            //.addStatement("return closure");
        } else {
            apiMethodBuilder.addStatement(
                    DAEMON_ENGINE_STRING + ".pursueQuest(new "
                            + currentMainQuestName + "MainQuest("
                            + prototypeMethodData.getArguments() + "))"
            );
        }


        return apiMethodBuilder.build();
    }

}