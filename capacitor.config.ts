import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.wanalab.godeye',
  appName: 'God Eye',
  webDir: 'www',
  backgroundColor: '#0B0E11',
  server: {
    androidScheme: 'https',
    cleartext: true
  },
  android: {
    allowMixedContent: true
  }
};

export default config;
