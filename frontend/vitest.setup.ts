import '@testing-library/jest-dom';

// Polyfill ResizeObserver for ReactFlow in JSDOM environment
class MockResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

global.ResizeObserver = global.ResizeObserver || MockResizeObserver;
