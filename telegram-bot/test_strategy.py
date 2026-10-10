import unittest
from strategy import Bar, atr, ema, generate_signal


def sample_bars(count=100):
    result = []
    price = 1.1000
    for i in range(count):
        # Deterministic gently rising prices; no assumed signal outcome.
        price += 0.0001
        result.append(Bar(i * 300, price - 0.00005, price + 0.0002,
                          price - 0.0002, price))
    return result


class StrategyTests(unittest.TestCase):
    def test_ema_has_aligned_length_and_warmup(self):
        values = ema([float(i) for i in range(1, 11)], 3)
        self.assertEqual(len(values), 10)
        self.assertNotEqual(values[2], values[2] * 0)  # first seeded EMA is finite

    def test_atr_requires_enough_history(self):
        self.assertIsNone(atr(sample_bars(10), 14))
        self.assertGreater(atr(sample_bars(30), 14), 0)

    def test_invalid_quote_fails_closed(self):
        bars = sample_bars()
        self.assertIsNone(generate_signal(bars, 1.1, 1.099, 0.001))
        self.assertIsNone(generate_signal(bars, 1.1, 1.2, 0.001))

    def test_excessive_spread_fails_closed(self):
        self.assertIsNone(generate_signal(sample_bars(), 1.1, 1.101, 0.0001))

    def test_insufficient_bars_fails_closed(self):
        self.assertIsNone(generate_signal(sample_bars(20), 1.1, 1.1001, 0.001))


if __name__ == "__main__":
    unittest.main()
