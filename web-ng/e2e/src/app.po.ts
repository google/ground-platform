import { browser, by, element } from 'protractor';

export class AppPage {
  navigateTo() {
    browser.get(browser.baseUrl);
  }

  getTitleText() {
    return element(by.css('app-root span')).getText() as Promise<string>;
  }
}
