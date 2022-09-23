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

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import junit.framework.TestCase;
import oap.reflect.Reflect;
import org.testng.annotations.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenapiSchemaTest extends TestCase {

    @Test
    public void testPrepareTypeForPrimitive() {
        Type type = OpenapiSchema.prepareType( Reflect.reflect( InnerType.class ).field( "a" ).orElseThrow().type() );
        assertThat( type ).isEqualTo( new TypeReference<Integer>() {}.getType() );
    }

    @Test
    public void testPrepareTypeForPrimitiveArray() {
        Type type = OpenapiSchema.prepareType( Reflect.reflect( InnerType.class ).field( "b" ).orElseThrow().type() );
        assertThat( type ).isEqualTo( new TypeReference<List<Integer>>() {}.getType() );
    }

    @Test
    public void testPrepareTypeForString() {
        Type type = OpenapiSchema.prepareType( Reflect.reflect( InnerType.class ).field( "str" ).orElseThrow().type() );
        assertThat( type ).isEqualTo( new TypeReference<String>() {}.getType() );
    }

    @Test
    public void testPrepareTypeForStringArray() {
        Type type = OpenapiSchema.prepareType( Reflect.reflect( InnerType.class ).field( "objarray" ).orElseThrow().type() );
        assertThat( type ).isEqualTo( new TypeReference<List<String>>() {}.getType() );
    }

    @Test
    public void testPrepareTypeForObjectList() {
        Type type = OpenapiSchema.prepareType( Reflect.reflect( InnerType.class ).field( "listApi" ).orElseThrow().type() );
        assertThat( type ).isEqualTo( new TypeReference<List<ApiInfo>>() {}.getType() );
    }

    @Test
    public void testCreateSchemaRefForNull() {
        assertThat( OpenapiSchema.createSchemaRef( null, Map.of() ) ).isNull();
    }

    @Test
    public void testCreateSchemaRefForSchemaWithEmptyName() {
        assertThat( OpenapiSchema.createSchemaRef( new StringSchema(), Map.of() ) )
            .isEqualTo( new StringSchema() );
    }

    @Test
    public void testCreateSchemaRefForSchemaForEmptyMap() {
        var resolvedSchema = ModelConverters.getInstance().readAllAsResolvedSchema( InnerType.class );
        assertThat( OpenapiSchema.createSchemaRef( resolvedSchema.schema, Map.of() ) )
            .isEqualTo( resolvedSchema.schema );
    }

    @Test
    public void testCreateSchemaRefForSchema() {
        var resolvedSchema = ModelConverters.getInstance().readAllAsResolvedSchema( InnerType.class );
        var expected = new io.swagger.v3.oas.models.media.Schema<>();
        expected.name( resolvedSchema.schema.getName() ).$ref( RefUtils.constructRef( resolvedSchema.schema.getName() ) );
        assertThat( OpenapiSchema.createSchemaRef( resolvedSchema.schema, resolvedSchema.referencedSchemas ) )
            .isEqualTo( expected );
    }

    @Test
    public void testPrepareSchemaShouldFillApiSchemaMap() {
        var openAPI = new OpenAPI();

        assertThat( openAPI.getComponents() ).isNull();

        var resolvedSchema = OpenapiSchema.prepareSchema( InnerType.class, openAPI );

        assertThat( resolvedSchema.referencedSchemas ).isEqualTo( openAPI.getComponents().getSchemas() );
        assertThat( resolvedSchema.schema ).isNotNull();
    }

    @Test
    public void testPrepareSchemaShouldNotFillApiSchemaMap() {
        var openAPI = new OpenAPI();

        assertThat( openAPI.getComponents() ).isNull();

        var resolvedSchema = OpenapiSchema.prepareSchema( String.class, openAPI );

        assertThat( openAPI.getComponents() ).isNull();
        assertThat( resolvedSchema.referencedSchemas ).isEmpty();
        assertThat( resolvedSchema.schema ).isNotNull();
    }

    @SuppressWarnings( "unchecked" )
    @Test
    public void testResolvedSchemaForInnerType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( InnerType.class );

        assertThat( resolvedSchema ).isNotNull();
        assertThat( resolvedSchema.schema.getProperties() ).hasSize( 9 );
        assertThat( resolvedSchema.schema.getName() ).isEqualTo( "InnerType" );
    }

    @Test
    public void testResolvedSchemaForStringType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( String.class );

        assertThat( resolvedSchema ).isNotNull();
        assertThat( resolvedSchema.schema.getClass() ).isEqualTo( StringSchema.class );
    }

    @Test
    public void testResolvedSchemaForOptionalType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( new TypeReference<Optional<String>>() {}.getType() );

        assertThat( resolvedSchema ).isNotNull();
        assertThat( resolvedSchema.schema.getClass() ).isEqualTo( StringSchema.class );
    }

    @Test
    public void testResolvedSchemaForListType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( new TypeReference<List<String>>() {}.getType() );

        assertThat( resolvedSchema ).isNotNull();
        assertThat( resolvedSchema.schema.getClass() ).isEqualTo( ArraySchema.class );
        assertThat( ( ( ArraySchema ) resolvedSchema.schema ).getItems().getClass() ).isEqualTo( StringSchema.class );
    }

    @Test
    public void testResolvedSchemaForComplexListType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( new TypeReference<Optional<List<List<String>>>>() {}.getType() );

        assertThat( resolvedSchema ).isNotNull();
        var schema = resolvedSchema.schema;
        assertThat( schema.getClass() ).isEqualTo( ArraySchema.class );
        var items = ( ( ArraySchema ) schema ).getItems();
        assertThat( items.getClass() ).isEqualTo( ArraySchema.class );
        assertThat( items.getItems().getClass() ).isEqualTo( StringSchema.class );
    }

    @Test
    public void testResolvedSchemaForComplexMapType() {

        ResolvedSchema resolvedSchema = OpenapiSchema.resolveSchema( new TypeReference<Optional<Map<String, List<String>>>>() {}.getType() );

        assertThat( resolvedSchema ).isNotNull();
        var schema = resolvedSchema.schema;
        assertThat( schema.getClass() ).isEqualTo( MapSchema.class );
        var properties = schema.getAdditionalProperties();
        assertThat( properties.getClass() ).isEqualTo( ArraySchema.class );
        assertThat( ( ( ArraySchema ) properties ).getItems().getClass() ).isEqualTo( StringSchema.class );
    }

    @SuppressWarnings( "unused" )
    public static class InnerType {
        public int a;
        public int[] b;
        public String str;
        public String[] objarray;
        public List<ApiInfo> listApi;
        public ApiInfo api;
        public Optional<List<ApiInfo>> optional;
        public Map<String, ApiInfo> map;
        public Map<ApiInfo, List<ApiInfo>> mapList;

    }
}