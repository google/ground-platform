/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

document.addEventListener("DOMContentLoaded", () => {
  // 1. Sticky Header Elevation on Scroll
  const header = document.getElementById("site-header");
  const onScroll = () => {
    if (!header) return;
    if (window.scrollY > 12) {
      header.classList.add("scrolled");
    } else {
      header.classList.remove("scrolled");
    }
  };
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  // 2. Mobile Navigation Toggle
  const menuToggle = document.getElementById("mobile-menu-toggle");
  const primaryNav = document.getElementById("primary-nav");
  if (menuToggle && primaryNav) {
    menuToggle.addEventListener("click", () => {
      const isOpen = primaryNav.classList.toggle("open");
      menuToggle.setAttribute("aria-expanded", String(isOpen));
    });

    primaryNav.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => {
        primaryNav.classList.remove("open");
        menuToggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  // 3. Hero Showcase Switcher (Web Console vs. Android Field App)
  const showcaseTabs = document.querySelectorAll("[data-showcase-target]");
  const showcasePanels = document.querySelectorAll("[data-showcase-panel]");
  const showcaseCaptionText = document.getElementById("showcase-caption-text");

  showcaseTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      const target = tab.getAttribute("data-showcase-target");
      const caption = tab.getAttribute("data-showcase-caption");

      showcaseTabs.forEach((t) => {
        t.classList.remove("active");
        t.setAttribute("aria-selected", "false");
      });
      tab.classList.add("active");
      tab.setAttribute("aria-selected", "true");

      showcasePanels.forEach((panel) => {
        if (panel.getAttribute("data-showcase-panel") === target) {
          panel.classList.add("active");
        } else {
          panel.classList.remove("active");
        }
      });

      if (showcaseCaptionText && caption) {
        showcaseCaptionText.textContent = caption;
      }
    });
  });

  // 4. Field Deployment Filter Tabs
  const filterBtns = document.querySelectorAll("[data-filter]");
  const deploymentCards = document.querySelectorAll("[data-categories]");

  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const selectedFilter = btn.getAttribute("data-filter");

      filterBtns.forEach((b) => {
        b.classList.remove("active");
        b.setAttribute("aria-pressed", "false");
      });
      btn.classList.add("active");
      btn.setAttribute("aria-pressed", "true");

      deploymentCards.forEach((card) => {
        const categories = (card.getAttribute("data-categories") || "").split(" ");
        if (selectedFilter === "all" || categories.includes(selectedFilter)) {
          card.classList.remove("hidden");
        } else {
          card.classList.add("hidden");
        }
      });
    });
  });

  // 5. Platform Workflow Step Switcher
  const workflowBtns = document.querySelectorAll("[data-workflow-step]");
  const workflowPanes = document.querySelectorAll("[data-workflow-pane]");

  workflowBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const step = btn.getAttribute("data-workflow-step");

      workflowBtns.forEach((b) => {
        b.classList.remove("active");
        b.setAttribute("aria-selected", "false");
      });
      btn.classList.add("active");
      btn.setAttribute("aria-selected", "true");

      workflowPanes.forEach((pane) => {
        if (pane.getAttribute("data-workflow-pane") === step) {
          pane.classList.add("active");
        } else {
          pane.classList.remove("active");
        }
      });
    });
  });

  // 6. Section Scroll-Spy for Navigation Highlight
  const sections = document.querySelectorAll("main section[id]");
  const navLinks = document.querySelectorAll(".primary-nav .nav-link");

  if ("IntersectionObserver" in window && sections.length > 0) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const id = entry.target.getAttribute("id");
            navLinks.forEach((link) => {
              if (link.getAttribute("href") === `#${id}`) {
                link.classList.add("active");
              } else {
                link.classList.remove("active");
              }
            });
          }
        });
      },
      { rootMargin: "-25% 0px -60% 0px", threshold: 0 }
    );

    sections.forEach((section) => observer.observe(section));
  }
});
