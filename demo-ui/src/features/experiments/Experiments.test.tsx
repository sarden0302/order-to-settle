// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { Experiments } from './Experiments';

afterEach(cleanup);
it('서버에 설정된 모니터링 주소만 새 창 링크로 보여준다', () => {
  render(<Experiments info={{ gateway: '', operatorServices: [], timeTravel: false, links: [{ title: 'Grafana', url: 'http://localhost:3000' }] }} />);
  const link = screen.getByRole('link', { name: 'Grafana' }) as HTMLAnchorElement;
  expect(link.href).toBe('http://localhost:3000/');
  expect(link.target).toBe('_blank');
});
