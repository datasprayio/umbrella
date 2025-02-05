/*
 * Copyright 2025 Matus Faro
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

import $RefParser from "@apidevtools/json-schema-ref-parser";
import {openapiSchemaToJsonSchema} from '@openapi-contrib/openapi-schema-to-json-schema';
import yaml from 'js-yaml';
import fs from 'fs';

// Read in two arguments, one for input file and one for output directory
if (process.argv.length !== 4) {
    console.error('Usage: node generateJsonSchema.js <apiYamlFile> <outDir>');
    process.exit(1);
}
const apiYamlFile = process.argv[2];
const outDir = process.argv[3];

(async () => {
    // Read and parse YAML
    const openApiDoc = yaml.load(fs.readFileSync(apiYamlFile, 'utf8'));

    // Resolve references within yaml file
    const openApiDocDereferenced = await $RefParser.dereference(openApiDoc, {
        dereference: {
            circular: false // Handle circular references if they exist
        }
    });

    // Prepare output folder
    fs.mkdirSync(outDir, {recursive: true});

    // Iterate over all endpoints and extract requests and responses
    const schemas = {}
    for (const [path, pathItem] of Object.entries(openApiDocDereferenced.paths)) {
        for (const [operation, operationItem] of Object.entries(pathItem)) {
            if (operationItem.requestBody) {
                for (const content of Object.values(operationItem.requestBody.content)) {
                    const schema = content.schema;
                    if (!schema.title) throw Error(`Schema requires a title under ${path} ${operation} requestBody`)
                    schemas[schema.title] = schema
                }
            }
            if (operationItem.responses) {
                for (const [response, responseItem] of Object.entries(operationItem.responses)) {
                    for (const content of Object.values(responseItem.content || {})) {
                        const schema = content.schema;
                        if (!schema.title) throw Error(`Schema requires a title under ${path} ${operation} responses ${response}`)
                        schemas[schema.title] = schema
                    }
                }
            }
        }
    }

    // Custom logic to merge particular schemas together
    // Disabled for now, merged manually
    [
        // ['HttpEvent', ['HttpEventRequest', 'HttpEventResponse']],
        // ['CustomEvent', ['EventRequest', 'EventResponse']],
    ].forEach(([title, parts]) => {
        const schema = {
            title,
            type: 'object',
            properties: {},
            required: []
        };
        parts.forEach(part => {
            const partSchema = schemas[part];
            if (!partSchema) throw Error(`Schema ${part} not found from ${Object.keys(schemas)}`);
            schema.properties = {...schema.properties, ...partSchema.properties};
            schema.required = [...schema.required, ...(partSchema.required || [])];
        });
        schemas[title] = schema;
    });

    // Write out schemas
    Object.entries(schemas).forEach(([name, schema]) => {
        const jsonSchema = openapiSchemaToJsonSchema(schema);
        fs.writeFileSync(`${outDir}/${name}.json`, JSON.stringify(jsonSchema, null, 2));
    });
})()
