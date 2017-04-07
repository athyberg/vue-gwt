package com.axellience.vuegwt.client.definitions;

import com.axellience.vuegwt.client.VueComponent;
import com.axellience.vuegwt.client.definitions.component.ComputedDefinition;
import com.axellience.vuegwt.client.definitions.component.DataDefinition;
import com.axellience.vuegwt.client.definitions.component.DataFactory;
import com.axellience.vuegwt.client.definitions.component.PropDefinition;
import com.axellience.vuegwt.client.gwtextension.TemplateResource;
import com.axellience.vuegwt.client.jsnative.types.JSON;
import com.axellience.vuegwt.client.jsnative.types.JsArray;
import com.axellience.vuegwt.client.jsnative.types.JsObject;
import com.axellience.vuegwt.client.jsnative.JsTools;
import com.axellience.vuegwt.client.jsnative.VueGwtTools;
import com.axellience.vuegwt.client.definitions.component.ComputedKind;
import com.google.gwt.regexp.shared.RegExp;
import jsinterop.annotations.JsType;

import java.util.List;

/**
 * Java representation of a Vue Component definition
 * Class extending this one are generated using the Annotation processor for each VueComponent
 * <p>
 * An instance of this Class can be immediately passed to Vue.JS instance where it's expecting a
 * component definition object.
 * <p>
 * This is an internal Class, it shouldn't be manipulated in applications that use VueGWT.
 * @author Adrien Baron
 */
@JsType
public abstract class VueComponentDefinition
{
    public VueComponent vuegwt$javaComponentInstance;

    public Object el;
    public String template;

    public Object data;
    public final JsObject        computed           = new JsObject();
    public final JsObject        methods            = new JsObject();
    public final JsObject        watch              = new JsObject();
    public final JsObject        props              = new JsObject();
    public final JsArray<String> vuegwt$collections = new JsArray<>();

    public final JsObject components = new JsObject();
    public final JsObject directives = new JsObject();

    public static final RegExp GET_FUNCTION_BODY =
        RegExp.compile("function\\s*[^)]*\\s*\\(\\)\\s*{\\s*(?:return|)\\s*([^;]*);?\\s*}");

    public static final RegExp REPLACE_THIS = RegExp.compile("(^|[^A-z0-9_\\$\\%])(this\\.)");

    public void setEl(Object el)
    {
        this.el = el;
    }

    protected void setTemplate(String template)
    {
        if ("".equals(template))
            JsTools.unsetObjectProperty(this, "template");
        else
            this.template = template;
    }

    /**
     * Set the template resource for the component
     * Put back the JS function in the template expressions
     * @param templateResource
     */
    protected void setTemplateResource(TemplateResource templateResource)
    {
        String templateText = templateResource.getText();
        // Empty template, nothing to do
        if ("".equals(templateText))
        {
            JsTools.unsetObjectProperty(this, "template");
            return;
        }

        // For each expression defined on our template
        int i = 0;
        String expressionId = TemplateResource.EXPRESSION_PREFIX + i;
        while (JsTools.objectHasProperty(templateResource, expressionId))
        {
            String expressionJavaFunction = JsTools.get(templateResource, expressionId).toString();
            String expression = GET_FUNCTION_BODY.exec(expressionJavaFunction).getGroup(1);

            expression = REPLACE_THIS.replace(expression, "$1");

            templateText = templateText.replaceAll(RegExp.quote(expressionId), expression);
            i++;
            expressionId = TemplateResource.EXPRESSION_PREFIX + i;
        }

        i = 0;
        String listId = TemplateResource.COLLECTION_PREFIX + i;
        while (JsTools.objectHasProperty(templateResource, listId))
        {
            vuegwt$collections.push(listId);
            methods.set(listId, JsTools.get(templateResource, listId));

            i++;
            listId = TemplateResource.COLLECTION_PREFIX + i;
        }
        this.setTemplate(templateText);
    }

    protected void initData(List<DataDefinition> dataDefinitions, boolean useFactory)
    {
        JsObject dataObject = new JsObject();
        for (DataDefinition dataDefinition : dataDefinitions)
        {
            Object dataDefaultValue =
                JsTools.getObjectProperty(vuegwt$javaComponentInstance, dataDefinition.javaName);

            if (dataDefaultValue == null)
            {
                dataDefaultValue = new JsObject();
            }

            dataObject.set(dataDefinition.jsName, dataDefaultValue);
        }

        // Create JsArrays for the Java collections to be copied into
        for (String collectionId : vuegwt$collections.iterate())
        {
            dataObject.set(
                collectionId + TemplateResource.COLLECTION_ARRAY_SUFFIX, new JsArray<>());
        }

        if (useFactory)
            this.data = (DataFactory) () -> JSON.parse(JSON.stringify(dataObject));
        else
            this.data = dataObject;
    }

    protected void addMethod(String javaName)
    {
        addMethod(javaName, javaName);
    }

    protected void addMethod(String javaName, String jsName)
    {
        abstractCopyJavaMethod(methods, javaName, javaName);
    }

    protected void addComputed(String javaName, String jsName, ComputedKind kind)
    {
        ComputedDefinition computedDefinition = computed.get(jsName);
        if (computedDefinition == null)
        {
            computedDefinition = new ComputedDefinition();
            computed.set(jsName, computedDefinition);
        }

        Object method = JsTools.getObjectProperty(vuegwt$javaComponentInstance, javaName);
        if (kind == ComputedKind.GETTER)
            computedDefinition.get = method;
        else if (kind == ComputedKind.SETTER)
            computedDefinition.set = method;
    }

    protected void addWatch(String javaName, String watchedPropertyName, boolean isDeep)
    {
        if (!isDeep)
        {
            abstractCopyJavaMethod(watch, javaName, watchedPropertyName);
            return;
        }

        JsObject watchDefinition = new JsObject();
        watchDefinition.set("deep", true);
        watchDefinition.set(
            "handler", JsTools.getObjectProperty(vuegwt$javaComponentInstance, javaName));
        watch.set(watchedPropertyName, watchDefinition);
    }

    protected void addLifecycleHook(String hookName)
    {
        JsTools.setObjectProperty(
            this, hookName, JsTools.getObjectProperty(vuegwt$javaComponentInstance, hookName));
    }

    protected void addProp(String javaName, String jsName, boolean required, String typeJsName)
    {
        PropDefinition propDefinition = new PropDefinition();
        propDefinition.required = required;
        if (JsTools.objectHasProperty(vuegwt$javaComponentInstance, javaName))
            propDefinition.defaultValue =
                JsTools.getObjectProperty(vuegwt$javaComponentInstance, javaName);

        if (typeJsName != null)
            propDefinition.type = JsTools.getNativeType(typeJsName);

        props.set(jsName, propDefinition);
    }

    protected void addPropValidator(String methodName, String propertyName)
    {
        PropDefinition propDefinition = props.get(propertyName);
        propDefinition.validator =
            JsTools.getObjectProperty(vuegwt$javaComponentInstance, methodName);
    }

    protected void addComponent(Class<? extends VueComponent> componentClass)
    {
        this.components.set(VueGwtTools.componentToTagName(componentClass),
            VueComponentDefinitionCache.getComponentDefinitionForClass(componentClass)
        );
    }

    private void abstractCopyJavaMethod(JsObject container, String javaName, String jsName)
    {
        container.set(jsName, JsTools.getObjectProperty(vuegwt$javaComponentInstance, javaName));
    }
}
