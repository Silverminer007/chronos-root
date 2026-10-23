import { browserOrder, detectBrowser } from './detect-browser';

const CHROME = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';
const EDGE = `${CHROME} Edg/130.0`;
const SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Safari/604.1';
const FIREFOX = 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0';

describe('detectBrowser', () => {
  it('erkennt Safari nur ohne Chrome', () => {
    expect(detectBrowser(SAFARI)).toBe('safari');
    expect(detectBrowser(CHROME)).toBe('chrome');
  });

  it('erkennt Firefox', () => expect(detectBrowser(FIREFOX)).toBe('firefox'));
  it('erkennt Edge als Chrome', () => expect(detectBrowser(EDGE)).toBe('chrome'));
  it('liefert unknown für unbekannte Browser', () => expect(detectBrowser('curl/8')).toBe('unknown'));
});

describe('browserOrder', () => {
  it('nutzt die Standardreihenfolge ohne erkannten Browser', () => {
    expect(browserOrder('unknown')).toEqual(['chrome', 'safari', 'firefox']);
  });

  it('stellt den erkannten Browser nach vorn', () => {
    expect(browserOrder('firefox')).toEqual(['firefox', 'chrome', 'safari']);
    expect(browserOrder('safari')).toEqual(['safari', 'chrome', 'firefox']);
    expect(browserOrder('chrome')).toEqual(['chrome', 'safari', 'firefox']);
  });
});
