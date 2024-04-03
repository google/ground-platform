/** All things Ground. */

import { WebElement } from 'selenium-webdriver'

export enum LoiType {
    DROP_PIN = 'pin',
    DRAW_AREA = 'area',
}

export enum TaskType {
    TEXT = 'text',
    SELECT_ONE = 'select one',
    SELECT_MULTIPLE = 'select multiple',
    NUMBER = 'number',
    DATE = 'date',
    TIME = 'time',
    PHOTO = 'photo',
    CAPTURE_LOCATION = 'capture location',
}

export enum Role {
    DATA_COLLECTOR = 'data collector',
    SURVEY_ORGANIZER = 'survey organizer',
    VIEWER = 'viewer',
}