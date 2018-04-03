/**
 * @customElement
 * @polymer
 */
class GndIcons extends Polymer.Element {
  static get is() { return 'gnd-icons'; }

  ready() {
    super.ready();
    // HACK: Find a better way to share icon SVGs.
    window.gndIcons = this;
  }

  getSvgPath(id) {
    // HACK: Find a better way to share icon SVGs.
    const icons = this.shadowRoot.querySelector('[name="feature-icons"]');
    const path = icons && icons.querySelector('[id="'+id+'"] path');
    return path && path.getAttribute('d');
  }
}
window.customElements.define(GndIcons.is, GndIcons);