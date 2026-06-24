package com.cmbc.oms.infrastructure.facadeimpl.apama;

import com.apama.event.Event;
import com.apama.event.parser.*;
import com.cmbc.oms.infrastructure.facadeimpl.apama.anno.EventField;
import com.cmbc.oms.infrastructure.facadeimpl.apama.bean.ApamaConstant;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * @author chendaqian
 * @date 2026/2/4
 * @time 18:40
 * @description
 */
public class ApamaCommonEvenetUtil {

    public static EventType getEventTypeByClass(String packageName, Class clazz) {
        EventField packageInfo = (EventField)clazz.getAnnotation(EventField.class);
        if (StringUtils.isEmpty(packageName) && packageInfo != null) {
            packageName = packageInfo.name();
        }

        if (StringUtils.isEmpty(packageName)) {
            throw new NullPointerException("未获取packageName");
        } else {
            EventType eventType = new EventType(packageName, new Field[0]);
            java.lang.reflect.Field[] fields = getFieldsByOrder(clazz);
            if (fields != null && fields.length > 0) {
                java.lang.reflect.Field[] var5 = fields;
                int var6 = fields.length;

                for(int var7 = 0; var7 < var6; ++var7) {
                    java.lang.reflect.Field field = var5[var7];
                    EventField fieldType = (EventField)field.getAnnotation(EventField.class);
                    if (fieldType != null) {
                        String simpleName = field.getType().getSimpleName();
                        String name = field.getName();
                        if (!StringUtils.isEmpty(fieldType.name())) {
                            name = fieldType.name();
                        }

                        if (!simpleName.equals("String") && !simpleName.equals("char")) {
                            if (!simpleName.equals("int") && !simpleName.equals("long") && !simpleName.equals("Integer")) {
                                if (simpleName.equals("boolean")) {
                                    eventType.addField(name, new SequenceFieldType(BooleanFieldType.TYPE));
                                } else if (simpleName.equals("List") || simpleName.equals("List") && "".equals(fieldType)) {
                                    eventType.addField(name, new SequenceFieldType(StringFieldType.TYPE));
                                } else if (!simpleName.equals("List") || !fieldType.fieldType().equals("int") && !fieldType.fieldType().equals("Integer")) {
                                    if (!simpleName.equals("List") || !fieldType.fieldType().equals("long") && !fieldType.fieldType().equals("Long")) {
                                        if (simpleName.equals("List") && (fieldType.fieldType().equals("float") || fieldType.fieldType().equals("Float"))) {
                                            eventType.addField(name, new SequenceFieldType(FloatFieldType.TYPE));
                                        } else if (simpleName.equals("List") && (fieldType.fieldType().equals("double") || fieldType.fieldType().equals("Double"))) {
                                            eventType.addField(name, new SequenceFieldType(FloatFieldType.TYPE));
                                        } else if (simpleName.equals("List") || fieldType.fieldType().equals("boolean") && !fieldType.fieldType().equals("Boolean")) {
                                            eventType.addField(name, new SequenceFieldType(BooleanFieldType.TYPE));
                                        } else if (simpleName.equals("Map")) {
                                            eventType.addField(name, new DictionaryFieldType(StringFieldType.TYPE, StringFieldType.TYPE));
                                        }
                                    } else {
                                        eventType.addField(name, new SequenceFieldType(IntegerFieldType.TYPE));
                                    }
                                } else {
                                    eventType.addField(name, new SequenceFieldType(IntegerFieldType.TYPE));
                                }
                            } else {
                                eventType.addField(name, FloatFieldType.TYPE);
                            }
                        } else {
                            eventType.addField(name, IntegerFieldType.TYPE);
                        }
                    } else {
                        eventType.addField(name, StringFieldType.TYPE);
                    }
                }
            }

            return eventType;
        }
    }

    public static EventType getEventTypeByClass(Class clazz) {
        return getEventTypeByClass((String)null, clazz);
    }

    public static Event getEventByObject(Object object) {
        return getEventByObject((String)null, object);
    }

    public static Event getEventByObject(String packageName, Object object) {
        Event event = new Event(getEventTypeByClass(packageName, object.getClass()));
        java.lang.reflect.Field[] fields = getFieldsByOrder(object.getClass());
        if (fields != null && fields.length > 0) {
            java.lang.reflect.Field[] var4 = fields;
            int var5 = fields.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                java.lang.reflect.Field field = var4[var6];
                EventField eventField = (EventField)field.getAnnotation(EventField.class);
                if (eventField != null) {
                    boolean accessFlag = field.isAccessible();
                    field.setAccessible(true);

                    Object o = null;
                    try {
                        o = field.get(object);
                    } catch (IllegalAccessException var12) {
                        // logger.error("IllegalAccessException:获取变量发生异常，field={}", field.toString());
                        continue;
                    }

                    String name = field.getName();
                    if (!StringUtils.isEmpty(eventField.name())) {
                        name = eventField.name();
                    }

                    if (!StringUtils.isEmpty(name) && o != null) {
                        if (field.getType().getSimpleName().equals("BigDecimal")) {
                            o = ((BigDecimal)o).doubleValue();
                        }

                        event.setField(name, o);
                    }

                    field.setAccessible(accessFlag);
                }
            }
        }

        return event;
    }

    private static java.lang.reflect.Field[] getFieldsByOrder(Class clazz) {
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

        for(int currentIndex = 0; currentIndex < fields.length; ++currentIndex) {
            EventField currentField = (EventField)fields[currentIndex].getAnnotation(EventField.class);
            if (currentField != null) {
                int cursor = currentIndex + 1;
                java.lang.reflect.Field minField = fields[currentIndex];
                int minOrder = currentField.order();
                int minIndex = currentIndex;

                while(cursor < fields.length) {
                    EventField eventField = (EventField)fields[cursor].getAnnotation(EventField.class);
                    if (eventField == null) {
                        ++cursor;
                    } else {
                        if (minOrder > eventField.order()) {
                            minOrder = eventField.order();
                            minField = fields[cursor];
                            minIndex = cursor;
                        }

                        ++cursor;
                    }
                }

                if (minIndex != currentIndex) {
                    java.lang.reflect.Field curField = fields[currentIndex];
                    fields[currentIndex] = minField;
                    fields[minIndex] = curField;
                }
            }
        }

        return fields;
    }

    public static boolean ignoreMessage(String displayName) {
        return ApamaConstant.DataViewIgnoreEnum.getNameset().contains(displayName);
    }

    // 从事件创建对象
    public static Object getObjectFromEvent(Event event, Class clazz) {
        // 根据事件类型和目标类型创建对象
        Object obj = null;
        try {
            obj = clazz.newInstance();

            // 这部分代码截图上有语法错误或缺失，只能按图原样誊写，注释掉以防编译失败
            //    // 将float转换为double
            //    field.set(obj, ((Float) value).doubleValue());
            //} else if (field.getType() == int.class && value instanceof Long) {
            //    // 将long转换为int
            //    field.set(obj, ((Long) value).intValue());
            //} else if (field.getType() == long.class && value instanceof Integer) {
            //    // 将integer转换为long
            //    field.set(obj, ((Integer) value).longValue());
            //} else {
            //    // 原始设置逻辑
            //    field.set(obj, value);
            //}
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create object from event", e);
        }
        return obj;
    }
}
