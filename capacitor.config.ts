import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.wanalab.godeye',
  appName: 'God Eye - WanaLab',
  webDir: 'www',
  server: {
    androidScheme: 'https',
    cleartext: true
  }
};

export default config;
