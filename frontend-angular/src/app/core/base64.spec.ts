import { base64ToUint8Array } from './base64';

describe('base64ToUint8Array', () => {
  it('dekodiert Base64 ohne Padding', () => {
    expect(Array.from(base64ToUint8Array('AQID'))).toEqual([1, 2, 3]);
    expect(Array.from(base64ToUint8Array('AQI'))).toEqual([1, 2]);
  });

  it('akzeptiert die URL-sichere Variante (- und _)', () => {
    expect(Array.from(base64ToUint8Array('-_8'))).toEqual([251, 255]);
  });
});
