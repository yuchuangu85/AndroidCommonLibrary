package com.codemx.annotationprocessor;

import com.google.auto.service.AutoService;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)// 声明Processor入口
@SupportedSourceVersion(SourceVersion.RELEASE_8)// jdk1.8
@SupportedAnnotationTypes("com.codemx.codemxannotion.CodeMxAnnotion")// 需要解析注解的路径
public class JavaAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        return false;
    }

}
