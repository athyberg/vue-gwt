/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.axellience.vuegwt.template;

import com.axellience.vuegwt.client.component.VueComponent;
import com.axellience.vuegwt.client.template.TemplateExpressionBase;
import com.axellience.vuegwt.client.template.TemplateExpressionKind;
import com.axellience.vuegwt.client.template.TemplateResource;
import com.axellience.vuegwt.jsr69.GenerationUtil;
import com.axellience.vuegwt.jsr69.component.TemplateProviderGenerator;
import com.axellience.vuegwt.jsr69.component.annotations.Computed;
import com.axellience.vuegwt.jsr69.style.StyleProviderGenerator;
import com.axellience.vuegwt.template.parser.TemplateParser;
import com.axellience.vuegwt.template.parser.result.TemplateExpression;
import com.axellience.vuegwt.template.parser.result.TemplateParserResult;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.dev.util.Util;
import com.google.gwt.resources.client.ClientBundle.Source;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.ext.AbstractResourceGenerator;
import com.google.gwt.resources.ext.ResourceContext;
import com.google.gwt.resources.ext.ResourceGeneratorUtil;
import com.google.gwt.resources.ext.SupportsGeneratorResultCaching;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwt.user.rebind.StringSourceWriter;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Original Source: GWT Project http://www.gwtproject.org/
 * <p>
 * Modified by Adrien Baron
 */
public final class TemplateResourceGenerator extends AbstractResourceGenerator
    implements SupportsGeneratorResultCaching
{
    /**
     * Java compiler has a limit of 2^16 bytes for encoding string constants in a
     * class file. Since the max size of a character is 4 bytes, we'll limit the
     * number of characters to (2^14 - 1) to fit within one record.
     */
    private static final int MAX_STRING_CHUNK = 16383;

    @Override
    public String createAssignment(TreeLogger logger, ResourceContext context, JMethod method)
    throws UnableToCompleteException
    {
        URL resource = getResource(logger, context, method);
        SourceWriter sw = new StringSourceWriter();

        // No resource for the template
        if (resource == null)
        {
            return generateEmptyTemplateResource(method, sw);
        }

        // Convenience when examining the generated code.
        if (!AbstractResourceGenerator.STRIP_COMMENTS)
            sw.println("// " + resource.toExternalForm());

        TypeOracle typeOracle = context.getGeneratorContext().getTypeOracle();
        String typeName = getTypeName(method);

        // Start class
        sw.println("new " + typeName + TemplateProviderGenerator.TEMPLATE_RESOURCE_SUFFIX + "() {");
        sw.indent();

        // Get template content from HTML file
        String templateContent = Util.readURLAsString(resource);

        // Process it and replace with the result
        TemplateParserResult templateParserResult =
            new TemplateParser().parseHtmlTemplate(templateContent, typeOracle.findType(typeName));
        templateContent = templateParserResult.getTemplateWithReplacements();

        // Computed properties
        JClassType componentClass = typeOracle.findType(typeName);
        Set<String> doneComputed = new HashSet<>();
        processComputedProperties(sw, componentClass, doneComputed);

        processComponentStyles(sw, templateParserResult);
        processTemplateString(method, sw, templateContent);
        processTemplateExpressions(sw, templateParserResult);

        sw.outdent();
        sw.println("}");

        return sw.toString();
    }

    private URL getResource(TreeLogger logger, ResourceContext context, JMethod method)
    {
        URL[] resources;
        try
        {
            resources = ResourceGeneratorUtil.findResources(logger, context, method);
        }
        catch (UnableToCompleteException e)
        {
            resources = null;
        }

        return resources == null ? null : resources[0];
    }

    private String generateEmptyTemplateResource(JMethod method, SourceWriter sw)
    {
        sw.println("new " + TemplateResource.class.getName() + "() {");
        sw.indent();
        sw.println("public String getText() {return \"\";}");
        sw.println("public String getName() {return \"" + method.getName() + "\";}");
        sw.outdent();
        sw.println("}");
        return sw.toString();
    }

    private String getTypeName(JMethod method)
    {
        Source resourceAnnotation = method.getAnnotation(Source.class);
        String resourcePath = resourceAnnotation.value()[0];
        return resourcePath.substring(0, resourcePath.length() - 5).replaceAll("/", ".");
    }

    private void processTemplateExpressions(SourceWriter sw, TemplateParserResult templateParserResult)
    {
        for (TemplateExpression expression : templateParserResult.getExpressions())
        {
            generateTemplateExpressionMethod(sw, expression);
        }

        generateGetTemplateExpressions(sw, templateParserResult);
    }

    private void generateTemplateExpressionMethod(SourceWriter sw, TemplateExpression expression)
    {
        String expressionReturnType = expression.getReturnType();
        if ("VOID".equals(expressionReturnType))
            expressionReturnType = "void";

        sw.println("@jsinterop.annotations.JsMethod");
        String[] parameters = expression
            .getParameters()
            .stream()
            .map(param -> param.getType() + " " + param.getName())
            .toArray(String[]::new);

        sw.println("public "
            + expressionReturnType
            + " "
            + expression.getId()
            + "("
            + String.join(", ", parameters)
            + ") {");
        sw.indent();

        if (isString(expressionReturnType))
        {
            sw.println("return (" + expression.getBody() + ") + \"\";");
        }
        else if ("void".equals(expressionReturnType))
        {
            sw.println(expression.getBody() + ";");
        }
        else
        {
            sw.println("return (" + expressionReturnType + ") (" + expression.getBody() + ");");
        }

        sw.outdent();
        sw.println("}");
    }

    private boolean isString(String expressionReturnType)
    {
        return "java.lang.String".equals(expressionReturnType) || "String".equals(
            expressionReturnType);
    }

    private void generateGetTemplateExpressions(SourceWriter sw,
        TemplateParserResult templateParserResult)
    {
        sw.println("public java.util.List getTemplateExpressions() {");
        sw.indent();
        sw.println("java.util.List result = new java.util.LinkedList();");
        for (TemplateExpression expression : templateParserResult.getExpressions())
        {
            sw.println("result.add(new "
                + TemplateExpressionBase.class.getCanonicalName()
                + "("
                + TemplateExpressionKind.class.getCanonicalName()
                + "."
                + expression.getKind().toString()
                + ", \""
                + expression.getId()
                + "\""
                + "));");
        }
        sw.println("return result;");
        sw.outdent();
        sw.println("}");
    }

    private void processTemplateString(JMethod method, SourceWriter sw, String templateContent)
    {
        sw.println("public String getText() {");
        sw.indent();
        if (templateContent.length() > MAX_STRING_CHUNK)
        {
            writeLongString(sw, templateContent);
        }
        else
        {
            sw.println("return \"" + Generator.escape(templateContent) + "\";");
        }
        sw.outdent();
        sw.println("}");

        sw.println("public String getName() {");
        sw.indent();
        sw.println("return \"" + method.getName() + "\";");
        sw.outdent();
        sw.println("}");
    }

    private void processComponentStyles(SourceWriter sw, TemplateParserResult templateParserResult)
    {
        for (Entry<String, String> entry : templateParserResult.getStyleImports().entrySet())
        {
            String styleInterfaceName = entry.getValue();
            String styleInstance = styleInterfaceName
                + StyleProviderGenerator.STYLE_BUNDLE_SUFFIX
                + ".INSTANCE."
                + StyleProviderGenerator.STYLE_BUNDLE_METHOD_NAME
                + "()";
            sw.println("@jsinterop.annotations.JsProperty");
            sw.println("private "
                + entry.getValue()
                + " "
                + entry.getKey()
                + " = "
                + styleInstance
                + ";");
        }

        String mapType =
            Map.class.getCanonicalName() + "<String, " + CssResource.class.getCanonicalName() + ">";
        sw.println("public " + mapType + "getTemplateStyles() { ");
        sw.indent();
        sw.println(mapType + " result = new " + HashMap.class.getCanonicalName() + "<>();");
        for (String styleName : templateParserResult.getStyleImports().keySet())
        {
            sw.println("result.put(\"" + styleName + "\", " + styleName + ");");
        }
        sw.println("return result;");
        sw.outdent();
        sw.println("}");
    }

    private void processComputedProperties(SourceWriter sw, JClassType vueComponentClass,
        Set<String> doneComputed)
    {
        if (vueComponentClass == null || vueComponentClass
            .getQualifiedSourceName()
            .equals(VueComponent.class.getCanonicalName()))
            return;

        for (JMethod jMethod : vueComponentClass.getMethods())
        {
            Computed computed = jMethod.getAnnotation(Computed.class);
            if (computed == null)
                continue;

            String propertyName =
                GenerationUtil.getComputedPropertyName(computed, jMethod.getName());
            if (doneComputed.contains(propertyName))
                continue;

            doneComputed.add(propertyName);
            sw.println("@jsinterop.annotations.JsProperty");
            sw.println(jMethod.getReturnType().getQualifiedSourceName() + " " + propertyName + ";");
        }
        processComputedProperties(sw, vueComponentClass.getSuperclass(), doneComputed);
    }

    /**
     * A single constant that is too long will crash the compiler with an out of
     * memory error. Break up the constant and generate code that appends using a
     * buffer.
     */

    private void writeLongString(SourceWriter sw, String toWrite)
    {
        sw.println("StringBuilder builder = new StringBuilder();");
        int offset = 0;
        int length = toWrite.length();
        while (offset < length - 1)
        {
            int subLength = Math.min(MAX_STRING_CHUNK, length - offset);
            sw.print("builder.append(\"");
            sw.print(Generator.escape(toWrite.substring(offset, offset + subLength)));
            sw.println("\");");
            offset += subLength;
        }
        sw.println("return builder.toString();");
    }
}
