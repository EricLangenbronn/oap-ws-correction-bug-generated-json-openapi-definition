/*
 * The MIT License (MIT)
 *
 * Copyright (c) Open Application Platform Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package oap.ws.openapi;

import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import lombok.extern.slf4j.Slf4j;
import oap.reflect.Reflection;
import oap.ws.openapi.swagger.DeprecationAnnotationResolver;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
class OpenapiSchema {
    private static final Map<String, Class<?>> namePrimitiveMap = new HashMap<>();
    private final ModelConverters converters = new ModelConverters();
    private final DeprecationAnnotationResolver converter;

    static {
        namePrimitiveMap.put( "boolean", Boolean.class );
        namePrimitiveMap.put( "byte", Byte.class );
        namePrimitiveMap.put( "char", Character.class );
        namePrimitiveMap.put( "short", Short.class );
        namePrimitiveMap.put( "int", Integer.class );
        namePrimitiveMap.put( "long", Long.class );
        namePrimitiveMap.put( "double", Double.class );
        namePrimitiveMap.put( "float", Float.class );
        namePrimitiveMap.put( "void", Void.class );
    }

    public OpenapiSchema() {
        ModelResolver modelConverter = ( ModelResolver ) converters.getConverters().get( 0 );
        converter = new DeprecationAnnotationResolver( modelConverter );
        converters.addConverter( converter );
    }

    /**
     * Prepares type for conversion to schema
     *
     * @param type - reflection class data
     * @return prepared type
     * @see io.swagger.v3.oas.models.media.Schema
     */
    public static Type prepareType( Reflection type ) {
        if( type.isPrimitive() ) {
            return namePrimitiveMap.get( type.name() );
        }
        if( type.isArray() ) {
            var componentType = type.underlying.componentType();
            if( componentType.isPrimitive() ) {
                componentType = namePrimitiveMap.get( componentType.getName() );
            }
            return TypeUtils.parameterize( List.class, componentType );
        }
        return type.getType();
    }

    /**
     * Returns schema object with reference to openapi components schema map
     *
     * @param schema - object to make a ref
     * @param map    - openapi components schema map
     * @return schema reference if openapi components schema map already contains schema with same name
     * otherwise return schema object without changes
     */
    public Schema createSchemaRef( Schema schema, Map<String, Schema> map ) {
        if( schema != null
            && schema.getName() != null
            && map.containsKey( schema.getName() ) ) {
            var result = new Schema<>();
            result.$ref( RefUtils.constructRef( schema.getName() ) );
            return result;
        }
        return schema;
    }

    /**
     * Prepares schema and add all necessary elements to openapi schema map
     *
     * @param type - Type to create openapi schema
     * @param api  - prepared openapi object
     * @return resolved schema
     * @see io.swagger.v3.oas.models.media.Schema
     */
    public ResolvedSchema prepareSchema( Type type, OpenAPI api ) {
        var resolvedSchema = resolveSchema( type );
        resolvedSchema.referencedSchemas.forEach( api::schema );
        return resolvedSchema;
    }

    /**
     * Transforms type into openapi schema
     *
     * @param type to transform
     * @return transformed schema
     * @see io.swagger.v3.oas.models.media.Schema
     */
    public ResolvedSchema resolveSchema( Type type ) {
        var resolvedSchema = converters.readAllAsResolvedSchema( type );
        if ( resolvedSchema == null || resolvedSchema.schema != null ) {
            return resolvedSchema;
        }
        if( type instanceof ParameterizedType paramType ) {
            var rawClass = TypeFactory.rawClass( type );
            if( isCollection( rawClass ) ) {
                var schema = new ArraySchema();
                var genericSchema = resolveSchema( getGenericType( paramType, 0 ) );
                schema.setItems( createSchemaRef( genericSchema.schema, resolvedSchema.referencedSchemas ) );
                resolvedSchema.schema = schema;
                log.debug( "Type {} resolved to {} (a collection)", type, rawClass );
            } else if( isMap( rawClass ) ) {
                var schema = new MapSchema();
                var genericSchema = resolveSchema( getGenericType( paramType, 1 ) );
                schema.setAdditionalProperties( genericSchema.schema );
                resolvedSchema.schema = schema;
                log.debug( "Type {} resolved to {} (a map)", type, rawClass );
            } else if( isOptional( rawClass ) ) {
                resolvedSchema = resolveSchema( getGenericType( paramType, 0 ) );
                log.debug( "Type {} resolved to {} (an optional)", type, rawClass );
            }
        }
        return resolvedSchema;
    }

    private boolean isOptional( Class<?> rawClass ) {
        return Optional.class.isAssignableFrom( rawClass );
    }

    private boolean isMap( Class<?> rawClass ) {
        return Map.class.isAssignableFrom( rawClass );
    }

    private boolean isCollection( Class<?> rawClass ) {
        return Collection.class.isAssignableFrom( rawClass );
    }

    private static Type getGenericType( ParameterizedType type, int number ) {
        if( type != null && type.getActualTypeArguments().length - 1 >= number ) {
            return type.getActualTypeArguments()[number];
        }
        return null;
    }

    public void processExtensionsInSchemas( Schema schema, String className, String fieldName ) {
        Schema ext = converter.getSchema( className, fieldName );
        if( ext == null ) return;
        schema.$ref( RefUtils.constructRef( ext.getName() ) );
    }
}
