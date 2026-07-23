import { chromium } from 'playwright'
import { execSync } from 'node:child_process'
import { mkdirSync, renameSync } from 'node:fs'
import { join } from 'node:path'

const BASE = process.env.DEMO_URL || 'http://localhost:5173'
const ARTIFACTS = '/opt/cursor/artifacts'
const TOTP_SECRET = 'JBSWY3DPEHPK3PXP'
const STEWARD_PASS = 'steward-dev-pass'

function totp() {
  return execSync(
    `python3 -c "import pyotp; print(pyotp.TOTP('${TOTP_SECRET}').now())"`,
  ).toString().trim()
}

async function title(page, text, ms = 2200) {
  await page.evaluate((t) => {
    let el = document.getElementById('demo-overlay')
    if (!el) {
      el = document.createElement('div')
      el.id = 'demo-overlay'
      el.style.cssText =
        'position:fixed;inset:0;background:rgba(0,0,0,0.82);display:flex;align-items:center;justify-content:center;z-index:99999;pointer-events:none;'
      document.body.appendChild(el)
    }
    el.innerHTML = `<div style="color:#1e4a7a;font-family:system-ui,sans-serif;text-align:center;padding:2rem;max-width:90%"><p style="font-size:0.85rem;letter-spacing:0.15em;text-transform:uppercase;color:#94a3b8;margin-bottom:0.75rem">Fight the Fascists</p><h1 style="font-size:1.6rem;font-weight:700;color:white;line-height:1.35;white-space:pre-line">${t}</h1></div>`
    el.style.display = 'flex'
  }, text)
  await page.waitForTimeout(ms)
  await page.evaluate(() => {
    const el = document.getElementById('demo-overlay')
    if (el) el.style.display = 'none'
  })
}

async function wait(page, ms = 1200) {
  await page.waitForTimeout(ms)
}

async function section(page, name, fn) {
  try {
    await fn()
  } catch (err) {
    console.warn(`Section skipped (${name}):`, err instanceof Error ? err.message : err)
  }
}

mkdirSync(ARTIFACTS, { recursive: true })
const videoDir = join(ARTIFACTS, 'demo-raw')
mkdirSync(videoDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({
  viewport: { width: 1280, height: 800 },
  recordVideo: { dir: videoDir, size: { width: 1280, height: 800 } },
  locale: 'en-US',
})
const page = await context.newPage()

try {
  await title(page, 'Product Demo\nMutual-aid coordination for protest sites')
  await page.goto(BASE)
  await wait(page, 1500)

  await section(page, 'chapter-picker', async () => {
    await title(page, '1 · Chapter Picker\nChoose a protest site (multi-chapter)')
    await page.waitForSelector('text=Delhi 2026')
    await wait(page, 1000)
    await page.click('text=Delhi 2026')
    await page.waitForURL('**/delhi-2026**')
    await wait(page, 1500)
  })

  await section(page, 'board', async () => {
    await title(page, '2 · Need Board\nLive supply needs with heat advisory')
    await wait(page, 2000)
  })

  const demoNote = 'FTF demo dry snacks Zone C'

  await section(page, 'post-need', async () => {
    await title(page, '3 · Post a Need (F1)\nAnonymous device + proof-of-work')
    await page.click('a:has-text("Post a Need")')
    await page.waitForURL('**/post')
    await wait(page, 800)
    await page.locator('select').first().selectOption({ index: 2 })
    await page.locator('select').nth(1).selectOption('FOOD_DRY')
    await page.fill('input[type=number]', '30')
    await page.getByRole('button', { name: 'Within 6h' }).click()
    await page.fill('textarea', demoNote)
    await page.getByRole('button', { name: 'Submit' }).click()
    await page.waitForURL('**/delhi-2026', { timeout: 180000 })
    await page.waitForSelector(`text=${demoNote}`, { timeout: 60000 })
    await wait(page, 1500)
  })

  await section(page, 'claim', async () => {
    await title(page, '4 · Claim / Help (F2)\nPartial pledges with handoff codes')
    let card = page.locator('article').filter({ hasText: demoNote })
    if ((await card.count()) === 0) {
      card = page.locator('article').filter({ hasText: 'Dry food' }).filter({ hasText: '0 /' }).first()
    }
    await card.getByRole('link', { name: 'Help' }).click({ timeout: 15000 })
    await page.waitForURL('**/claim/**')
    await wait(page, 1000)
    await page.getByRole('button', { name: 'Confirm' }).click()
    await wait(page, 800)
    await page.getByRole('button', { name: '1h' }).click()
    await page.getByRole('button', { name: 'Help' }).click()
    await page.waitForSelector('text=Your handoff code', { timeout: 180000 })
    await wait(page, 2500)
    await page.getByRole('button', { name: 'Need Board' }).click()
    await wait(page, 1500)
  })

  await section(page, 'shifts', async () => {
    await title(page, '5 · Volunteer Shifts (F4)\n3-hour blocks with signup caps')
    await page.click('a:has-text("Shifts")')
    await page.waitForURL('**/shifts')
    await wait(page, 2000)
    const signup = page.getByRole('button', { name: 'Sign up' }).first()
    if (await signup.isVisible()) await signup.click()
    await wait(page, 2000)
  })

  await section(page, 'announcements', async () => {
    await title(page, '6 · Announcements (F7)\nVerified logistics feed')
    await page.click('a:has-text("Announcements")')
    await page.waitForURL('**/announce')
    await wait(page, 2000)
  })

  await section(page, 'aid', async () => {
    await title(page, '7 · First Aid Directory (F5)\nAid points with status')
    await page.click('a:has-text("First Aid")')
    await page.waitForURL('**/aid')
    await wait(page, 2000)
  })

  await section(page, 'bulk-give', async () => {
    await title(page, '8 · Bulk Give (F8)\nOrganisation supply pledges')
    await page.click('a:has-text("Bulk Give")')
    await page.waitForURL('**/give')
    await wait(page, 800)
    await page.fill('input[placeholder*="Langar"]', 'Demo Langar Collective')
    await page.fill('input[type=number]', '150')
    await page.getByRole('button', { name: 'Dinner 8pm' }).click()
    await page.locator('input[type=checkbox]').check()
    await page.getByRole('button', { name: 'Submit pledge' }).click()
    await wait(page, 2500)
  })

  await section(page, 'about', async () => {
    await title(page, '9 · Transparency (P2)\nAggregate stats, PDF board, mirror')
    await page.click('a:has-text("About")')
    await page.waitForURL('**/about')
    await wait(page, 2500)
  })

  await section(page, 'i18n', async () => {
    await title(page, '10 · Hindi / English i18n')
    await page.getByRole('button', { name: 'Toggle language' }).click()
    await wait(page, 2000)
    await page.getByRole('button', { name: 'Toggle language' }).click()
    await wait(page, 1000)
  })

  await section(page, 'steward', async () => {
    await title(page, '11 · Steward Console (F6)\nPassphrase + TOTP, moderation queue')
    await page.goto(`${BASE}/delhi-2026/steward`)
    await wait(page, 1000)
    await page.fill('input[type=password]', STEWARD_PASS)
    await page.fill('input[inputmode=numeric]', totp())
    await page.getByRole('button', { name: 'Login' }).click()
    await page.waitForSelector('text=Moderation queue', { timeout: 15000 })
    await wait(page, 1500)
    await page.fill('textarea', 'Demo: Water refill arriving at Gate 2 in 20 minutes.')
    await page.getByRole('button', { name: 'Submit' }).click()
    await wait(page, 2000)
  })

  await section(page, 'lite', async () => {
    await title(page, '12 · Lite Mode\nZero-JS fallback board')
    await page.goto('http://localhost:8080/api/v1/chapters/delhi-2026/lite')
    await wait(page, 2500)
  })

  await section(page, 'final-board', async () => {
    await title(page, '13 · Live Board\nNeeds, announcements & forecast')
    await page.goto(`${BASE}/delhi-2026`)
    await wait(page, 3000)
  })

  await title(page, 'End of Demo\nfight-the-fascists.com/{chapter}')
  await wait(page, 2500)
} catch (err) {
  console.error('Demo fatal error:', err)
  await title(page, `Demo interrupted:\n${err instanceof Error ? err.message : String(err)}`, 3000)
} finally {
  const video = page.video()
  await context.close()
  await browser.close()

  if (video) {
    const webmPath = await video.path()
    const outWebm = join(ARTIFACTS, 'fight-the-fascists-demo.webm')
    const outMp4 = join(ARTIFACTS, 'fight-the-fascists-demo.mp4')
    renameSync(webmPath, outWebm)
    execSync(
      `ffmpeg -y -i "${outWebm}" -c:v libx264 -preset fast -crf 23 -pix_fmt yuv420p -movflags +faststart "${outMp4}"`,
      { stdio: 'inherit' },
    )
    console.log('Video saved:', outMp4)
    console.log('WebM saved:', outWebm)
  }
}
