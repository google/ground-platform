/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Injectable } from '@angular/core';
import { AngularFirestore, DocumentData } from '@angular/fire/firestore';
import { Observable } from 'rxjs';
import { Project } from '../../shared/models/project.model';
import { map } from 'rxjs/operators';
import { User } from './../../shared/models/user.model';
import { AuditInfo } from '../../shared/models/audit-info.model';
import { Feature } from '../../shared/models/feature.model';
import { Form } from '../../shared/models/form/form.model';
import { Field, FieldType } from '../../shared/models/form/field.model';
import {
  MultipleChoice,
  Cardinality,
} from '../../shared/models/form/multiple-choice.model';
import { Option } from '../../shared/models/form/option.model';
import { Layer } from './../../shared/models/layer.model';
import { List, Map } from 'immutable';
import { Observation } from '../../shared/models/observation/observation.model';
import { Response } from '../../shared/models/observation/response.model';
import { StringMap } from './../../shared/models/string-map.model';

/**
 * Helper to return either the keys of a dictionary, or if missing, returns an
 * empty array.
 */
// tslint:disable-next-line:no-any
function keys(dict?: any): any[] {
  return Object.keys(dict || {});
}

// TODO: Make DataStoreService and interface and turn this into concrete
// implementation (e.g., CloudFirestoreService).
@Injectable({
  providedIn: 'root',
})
export class DataStoreService {
  constructor(private db: AngularFirestore) {}

  /**
   * Returns an Observable that loads and emits the project with the specified
   * uuid.
   *
   * @param id the id of the requested project.
   */
  loadProject$(id: string) {
    return this.db
      .collection('projects')
      .doc(id)
      .get()
      .pipe(
        // Convert object to Project instance.
        map(doc => DataStoreService.toProject(doc.id, doc.data()!))
      );
  }

  updateProjectTitle(projectId: string, newTitle: string) {
    return this.db
      .collection('projects')
      .doc(projectId)
      .set({ title: { en: newTitle } }, { merge: true })
      .then(() => projectId);
  }

  // TODO: Define return types for methods in this class
  updateLayer(projectId: string, layer: Layer) {
    return this.db
      .collection('projects')
      .doc(projectId)
      .update({
        [`layers.${layer.id}`]: DataStoreService.layerToJS(layer),
      });
  }

  /**
   * Returns an Observable that loads and emits the feature with the specified
   * uuid.
   *
   * @param projectId the id of the project in which requested feature is.
   * @param featureId the id of the requested feature.
   */
  loadFeature$(projectId: string, featureId: string) {
    return this.db
      .collection(`projects/${projectId}/features`)
      .doc(featureId)
      .get()
      .pipe(map(doc => DataStoreService.toFeature(doc.id, doc.data()!)));
  }

  /**
   * Returns a stream containing the user with the specified id. Remote changes
   * to the user will cause a new value to be emitted.
   *
   * @param uid the unique id used to represent the user in the data store.
   */
  user$(uid: string): Observable<User | undefined> {
    return this.db.doc<User>(`users/${uid}`).valueChanges();
  }

  features$({ id }: Project): Observable<List<Feature>> {
    return this.db
      .collection(`projects/${id}/features`)
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(array.map(obj => DataStoreService.toFeature(obj.id, obj)))
        )
      );
  }

  /**
   * Returns an Observable that loads and emits the observations with the specified
   * uuid.
   *
   * @param id the id of the requested project (it should have forms inside).
   * @param featureId the id of the requested feature.
   */
  observations$(
    project: Project,
    feature: Feature
  ): Observable<List<Observation>> {
    return this.db
      .collection(`projects/${project.id}/observations`, ref =>
        ref.where('featureId', '==', feature.id)
      )
      .valueChanges({ idField: 'id' })
      .pipe(
        map(array =>
          List(
            array.map(obj =>
              DataStoreService.toObservation(project, feature, obj.id, obj)
            )
          )
        )
      );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Project instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toProject(id: string, data: DocumentData): Project {
    return new Project(
      id,
      StringMap(data.title),
      StringMap(data.description),
      Map<string, Layer>(
        keys(data.layers).map((id: string) => [
          id as string,
          DataStoreService.toLayer(id, data.layers[id]),
        ])
      )
    );
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Layer instance.
   *
   * @param id the uuid of the layer instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toLayer(id: string, data: DocumentData): Layer {
    return new Layer(
      id,
      data.color,
      StringMap(data.name),
      Map<string, Form>(
        keys(data.forms).map((id: string) => [
          id as string,
          DataStoreService.toForm(id, data.forms[id]),
        ])
      )
    );
  }

  private static layerToJS(layer: Layer): {} {
    const { id: layerId, name, forms, ...layerDoc } = layer;
    return {
      name: name?.toJS() || {},
      forms:
        forms
          ?.valueSeq()
          .reduce(
            (map, form) => ({ ...map, [form.id]: this.formToJS(form) }),
            {}
          ) || {},
      ...layerDoc,
    };
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Form instance.
   *
   * @param id the uuid of the form instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toForm(id: string, data: DocumentData): Form {
    return new Form(
      id,
      Map<string, Field>(
        keys(data.elements).map((id: string) => [
          id as string,
          DataStoreService.toField(id, data.elements[id]),
        ])
      )
    );
  }

  private static formToJS(form: Form): {} {
    const { fields, ...formDoc } = form;
    return {
      fields:
        fields?.reduce(
          (map, field: Field) => ({
            ...map,
            [field.id]: this.fieldToJS(field),
          }),
          {}
        ) || {},
      ...formDoc,
    };
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Field instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   index: 0,
   *   label: { 'en': 'Question 1' },
   *   required: true,
   *   type: 'text_field'
   * }
   * </code></pre>
   * or
   * <pre><code>
   * {
   *   index: 1,
   *   label: { 'en': 'Question 2' },
   *   required: false,
   *   type: 'multiple_choice',
   *   cardinality: 'select_one',
   *   options: {
   *     option001: {
   *       index: 0,
   *       code: 'A',
   *       label: { 'en': 'Option A' }
   *     },
   *     // ...
   *   }
   * </code></pre>
   */
  private static toField(id: string, data: DocumentData): Field {
    return new Field(
      id,
      DataStoreService.stringToFieldType(data.type),
      StringMap(data.label),
      data.required,
      data.options &&
        new MultipleChoice(
          DataStoreService.stringToCardinality(data.cardinality),
          Map<string, Option>(
            keys(data.options).map((id: string) => [
              id as string,
              DataStoreService.toOption(id, data.options[id]),
            ])
          )
        )
    );
  }

  private static fieldToJS(field: Field): {} {
    const { type, label, multipleChoice, ...fieldDoc } = field;
    if (multipleChoice === undefined) {
      return {
        type: DataStoreService.fieldTypeToString(type),
        label: label.toJS(),
        ...fieldDoc,
      };
    } else {
      return {
        type: DataStoreService.fieldTypeToString(type),
        label: label.toJS(),
        cardinality: DataStoreService.cardinalityToString(
          multipleChoice.cardinality
        ),
        // convert list of options to map of optionId: option.
        options:
          multipleChoice?.options?.reduce(
            (map, option: Option) => ({
              ...map,
              [option.id]: DataStoreService.optionToJS(option),
            }),
            {}
          ) || {},
        ...fieldDoc,
      };
    }
  }

  private static stringToCardinality(cardinality: string): Cardinality {
    switch (cardinality) {
      case 'select_one':
        return Cardinality.SELECT_ONE;
      case 'select_multiple':
        return Cardinality.SELECT_MULTIPLE;
      default:
        throw Error(`Unsupported cardinality ${cardinality}`);
    }
  }

  private static cardinalityToString(cardinality: Cardinality): string {
    switch (cardinality) {
      case Cardinality.SELECT_ONE:
        return 'select_one';
      case Cardinality.SELECT_MULTIPLE:
        return 'select_multiple';
      default:
        throw Error(`Unsupported cardinality ${cardinality}`);
    }
  }

  private static stringToFieldType(fieldType: string): FieldType {
    switch (fieldType) {
      case 'text_field':
        return FieldType.TEXT;
      case 'multiple_choice':
        return FieldType.MULTIPLE_CHOICE;
      case 'photo':
        return FieldType.PHOTO;
      default:
        throw Error(`Unsupported field type ${fieldType}`);
    }
  }

  private static fieldTypeToString(fieldType: FieldType): string {
    switch (fieldType) {
      case FieldType.TEXT:
        return 'text_field';
      case FieldType.MULTIPLE_CHOICE:
        return 'multiple_choice';
      case FieldType.PHOTO:
        return 'photo';
      default:
        throw Error(`Unsupported field type ${fieldType}`);
    }
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Field instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *    index: 0,
   *    code: 'A',
   *    label: { 'en': 'Option A' }
   *  }
   * </code></pre>
   */
  private static toOption(id: string, data: DocumentData): Option {
    return new Option(id, data.code, StringMap(data.label));
  }

  private static optionToJS(option: Option): {} {
    const { label, ...optionDoc } = option;
    return { 
      label: label.toJS(), 
      ...optionDoc, 
    };
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Feature instance.
   *
   * @param id the uuid of the project instance.
   * @param data the source data in a dictionary keyed by string.
   */
  private static toFeature(id: string, data: DocumentData): Feature {
    if (data === undefined) {
      throw Error(`Feature ${id} does not have document data.`);
    }
    return new Feature(id, data.layerId, data.location);
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable Observation instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   featureId: 'feature123'
   *   formId: 'form001',
   *   responses: {
   *     'element001': 'Response text',  // For 'text_field  elements.
   *     'element002': ['A', 'B'],       // For 'multiple_choice' elements.
   *      // ...
   *   }
   *   created: <AUDIT_INFO>,
   *   lastModified: <AUDIT_INFO>
   * }
   * </code></pre>
   */
  private static toObservation(
    project: Project,
    feature: Feature,
    id: string,
    data: DocumentData
  ): Observation {
    if (data === undefined) {
      throw Error(`Observation ${id} does not have document data.`);
    }
    const form = project.getForm(feature.layerId, data.formId);
    return new Observation(
      id,
      form,
      DataStoreService.toAuditInfo(data.created),
      DataStoreService.toAuditInfo(data.lastModified),
      Map<string, Response>(
        keys(data.responses).map((fieldId: string) => [
          fieldId as string,
          DataStoreService.toResponse(form, fieldId, data.responses[fieldId]),
        ])
      )
    );
  }

  private static toResponse(
    form: Form,
    fieldID: string,
    responseValue: string | List<string>
  ): Response {
    if (typeof responseValue === 'string') {
      return new Response(responseValue as string);
    }
    if (responseValue instanceof Array) {
      return new Response(
        List(
          responseValue.map(optionId =>
            form.getMultipleChoiceFieldOption(fieldID, optionId)
          )
        )
      );
    }
    throw Error(`Unknown value type ${typeof responseValue}`);
  }

  /**
   * Converts the raw object representation deserialized from Firebase into an
   * immutable AuditInfo instance.
   *
   * @param data the source data in a dictionary keyed by string.
   * <pre><code>
   * {
   *   user: {
   *     id: ...,
   *     displayName: ...,
   *     email: ...
   *   },
   *   clientTimestamp: ...,
   *   serverTimestamp: ...
   * }
   * </code></pre>
   */
  private static toAuditInfo(data: DocumentData): AuditInfo {
    return new AuditInfo(
      data.user,
      data.clientTimestamp?.toDate(),
      data.serverTimestamp?.toDate()
    );
  }

  generateId() {
    return this.db.collection('ids').ref.doc().id;
  }
}
