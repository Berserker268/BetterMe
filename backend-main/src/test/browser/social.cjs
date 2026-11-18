// Run SocialBrowserFixture first. Install playwright-core under target/social-browser-tools.
const { chromium } = require('../../../target/social-browser-tools/node_modules/playwright-core');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = 'http://127.0.0.1:18081';

(async () => {
  fs.mkdirSync('target/social-browser-results', { recursive: true });
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const errors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    async function login(target, email, hasPosts = true) {
      await target.goto(base + '/login');
      await target.locator('input[name="username"]').fill(email);
      await target.locator('input[name="password"]').fill('SocialBrowserTest123!');
      await target.getByRole('button', { name: 'Log in', exact: true }).click();
      await target.waitForURL('**/home');
      await target.goto(base + '/social');
      await target.locator(hasPosts ? '.social-post' : '.social-empty').first().waitFor();
    }
    await login(page, 'mahdi@example.test');
    await page.locator('#request-list .social-person').waitFor();
    await page.screenshot({ path: 'target/social-browser-results/desktop.png', fullPage: true });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Desktop overflows horizontally');
    await page.locator('#friend-search').fill('Daniel');
    await page.locator('#search-results').getByRole('button', { name: 'Add friend' }).click();
    await page.locator('#search-results').getByText('Request sent', { exact: true }).waitFor();
    await page.locator('#request-list').getByRole('button', { name: 'Accept', exact: true }).click();
    await page.locator('#friend-list').getByText('Emily Chen', { exact: true }).waitFor();

    const message = 'Browser-tested milestone <script>window.socialUnsafe = true</script>';
    await page.locator('#post-text').fill(message);
    await page.locator('#post-category').selectOption('Study');
    await page.locator('#post-photo').setInputFiles('target/social-browser-results/desktop.png');
    await page.locator('#photo-preview-wrap').waitFor({ state: 'visible' });
    await page.locator('#remove-photo').click();
    assert(await page.locator('#photo-preview-wrap').isHidden());
    await page.locator('#post-photo').setInputFiles('target/social-browser-results/desktop.png');
    await page.route('**/social/api/posts', route => route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: 'Test upload failure. Please try again.' }) }));
    await page.locator('#publish-post').click();
    await page.locator('#post-error').getByText('Test upload failure. Please try again.', { exact: true }).waitFor();
    assert.equal(await page.locator('#post-text').inputValue(), message);
    assert(await page.locator('#photo-preview-wrap').isVisible(), 'Failed post loses selected photo');
    await page.unroute('**/social/api/posts');
    await page.locator('#publish-post').click();
    await page.locator('.social-post-text').getByText(message, { exact: true }).waitFor();
    assert.equal(await page.evaluate(() => window.socialUnsafe), undefined);
    const newest = page.locator('.social-post').first();
    const photoPath = await newest.locator('.social-post-photo').getAttribute('src');
    assert.equal((await page.request.get(base + photoPath)).status(), 200);
    await newest.locator('.social-like').click();
    await page.waitForFunction(() => document.querySelector('.social-like').getAttribute('aria-pressed') === 'true');
    await newest.getByRole('button', { name: /^Comment/ }).click();
    await newest.locator('.social-comment-form textarea').fill('Well done <b>friend</b>!');
    await newest.getByRole('button', { name: 'Send', exact: true }).click();
    await newest.locator('.social-comment p').getByText('Well done <b>friend</b>!', { exact: true }).waitFor();

    const outsider = await browser.newContext();
    const otherPage = await outsider.newPage();
    await login(otherPage, 'daniel@example.test', false);
    assert.equal((await otherPage.request.get(base + photoPath)).status(), 404);
    await outsider.close();

    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'target/social-browser-results/mobile-feed.png', fullPage: true });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile overflows horizontally');
    assert(await page.locator('#social-friends-column').isHidden());
    await page.locator('#friends-tab').click();
    await page.locator('#social-friends-column').waitFor({ state: 'visible' });
    await page.screenshot({ path: 'target/social-browser-results/mobile-friends.png', fullPage: true });
    assert(await page.locator('#social-feed-column').isHidden());
    assert.deepEqual(errors, []);
    console.log('PASS: desktop/mobile layout, search, request/accept, photo preview/remove/post, likes, comments, escaped text, and unrelated photo denial.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
