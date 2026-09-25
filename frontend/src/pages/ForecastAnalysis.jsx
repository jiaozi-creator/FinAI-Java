import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { TrendingUp, Activity, BarChart3, Lightbulb, AlertTriangle } from 'lucide-react';
import axios from 'axios';
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts';

const ForecastAnalysis = () => {
  const { taskId } = useParams();
  const [forecast, setForecast] = useState(null);
  const [loading, setLoading] = useState(true);
  const [periods, setPeriods] = useState(3);
  const [selectedMetric, setSelectedMetric] = useState(null);

  const API_BASE = 'http://localhost:8080/api';

  useEffect(() => {
    loadForecast();
  }, [taskId, periods]);

  const loadForecast = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE}/forecast/${taskId}?periods=${periods}`);
      setForecast(response.data);
      if (response.data.metricForecasts && response.data.metricForecasts.length > 0) {
        setSelectedMetric(response.data.metricForecasts[0]);
      }
    } catch (error) {
      console.error('Failed to load forecast:', error);
    } finally {
      setLoading(false);
    }
  };

  const prepareChartData = (metric) => {
    if (!metric) return [];

    const historical = metric.historicalValues?.map(v => ({
      period: v.period,
      actual: v.value,
      type: 'historical'
    })) || [];

    const forecast = metric.forecastValues?.map((v, i) => ({
      period: v.period,
      forecast: v.value,
      upper: metric.upperBound?.[i]?.value,
      lower: metric.lowerBound?.[i]?.value,
      type: 'forecast'
    })) || [];

    return [...historical, ...forecast];
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-green-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-b-4 border-green-600 mx-auto mb-4"></div>
          <p className="text-gray-600 font-medium">Generating forecast...</p>
        </div>
      </div>
    );
  }

  if (!forecast) {
    return (
      <div className="flex items-center justify-center h-screen bg-gradient-to-br from-gray-50 to-green-50">
        <p className="text-gray-600">No forecast data available</p>
      </div>
    );
  }

  const chartData = prepareChartData(selectedMetric);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-green-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2 flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-green-500 to-emerald-600 rounded-xl flex items-center justify-center">
              <TrendingUp className="text-white" size={24} />
            </div>
            Forecast Analysis
          </h1>
          <p className="text-gray-600 ml-13">
            {forecast.companyName} ({forecast.companyCode}) - Base: {forecast.basePeriod}
          </p>
        </div>

        {/* Controls */}
        <div className="bg-white rounded-2xl shadow-lg p-6 mb-6 border border-gray-100">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <label className="text-sm font-bold text-gray-700">Forecast Period:</label>
              <div className="flex gap-2">
                {[1, 3, 5].map(p => (
                  <button
                    key={p}
                    onClick={() => setPeriods(p)}
                    className={`px-6 py-2.5 rounded-xl font-medium transition-all duration-200 ${
                      periods === p
                        ? 'bg-gradient-to-r from-green-500 to-emerald-600 text-white shadow-md'
                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  >
                    {p} Year{p > 1 ? 's' : ''}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex items-center gap-3 text-sm">
              <Activity className="text-green-600" size={18} />
              <span className="text-gray-600">Method:</span>
              <span className="font-bold text-green-600">{forecast.method}</span>
            </div>
          </div>
        </div>

        {/* Metric Selector */}
        <div className="bg-white rounded-2xl shadow-lg p-6 mb-6 border border-gray-100">
          <h2 className="text-lg font-bold text-gray-900 mb-4">Select Metric</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {forecast.metricForecasts?.map((metric, index) => (
              <button
                key={index}
                onClick={() => setSelectedMetric(metric)}
                className={`p-4 rounded-xl border-2 transition-all duration-200 ${
                  selectedMetric?.metricName === metric.metricName
                    ? 'border-green-500 bg-gradient-to-br from-green-50 to-emerald-50 shadow-md scale-105'
                    : 'border-gray-200 hover:border-green-300 hover:bg-gray-50'
                }`}
              >
                <div className="font-bold text-gray-900">{metric.displayName}</div>
                <div className="text-xs text-gray-600 mt-1">{metric.unit}</div>
              </button>
            ))}
          </div>
        </div>

        {/* Chart */}
        {selectedMetric && (
          <div className="bg-white rounded-2xl shadow-lg p-6 mb-6 border border-gray-100">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                <BarChart3 className="text-green-500" size={24} />
                {selectedMetric.displayName} Forecast
              </h2>
              <div className="text-sm bg-gradient-to-r from-green-100 to-emerald-100 px-4 py-2 rounded-lg">
                <span className="text-green-700 font-medium">Confidence:</span>{' '}
                <span className="font-bold text-green-900">{(selectedMetric.confidenceLevel * 100).toFixed(0)}%</span>
              </div>
            </div>
            <ResponsiveContainer width="100%" height={400}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="colorActual" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3B82F6" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#3B82F6" stopOpacity={0.1}/>
                  </linearGradient>
                  <linearGradient id="colorForecast" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#10B981" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#10B981" stopOpacity={0.1}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis dataKey="period" stroke="#6B7280" style={{ fontSize: '12px' }} />
                <YAxis stroke="#6B7280" style={{ fontSize: '12px' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'white',
                    border: '2px solid #10B981',
                    borderRadius: '12px',
                    padding: '12px'
                  }}
                />
                <Legend />
                <Area
                  type="monotone"
                  dataKey="actual"
                  stroke="#3B82F6"
                  strokeWidth={3}
                  fillOpacity={1}
                  fill="url(#colorActual)"
                  name="Actual"
                />
                <Area
                  type="monotone"
                  dataKey="forecast"
                  stroke="#10B981"
                  strokeWidth={3}
                  fillOpacity={1}
                  fill="url(#colorForecast)"
                  name="Forecast"
                />
                <Line
                  type="monotone"
                  dataKey="upper"
                  stroke="#9CA3AF"
                  strokeWidth={2}
                  strokeDasharray="5 5"
                  dot={false}
                  name="Upper Bound"
                />
                <Line
                  type="monotone"
                  dataKey="lower"
                  stroke="#9CA3AF"
                  strokeWidth={2}
                  strokeDasharray="5 5"
                  dot={false}
                  name="Lower Bound"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {/* Scenarios */}
        {forecast.scenarios && Object.keys(forecast.scenarios).length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
            {Object.entries(forecast.scenarios).map(([key, scenario]) => (
              <div key={key} className="bg-white rounded-2xl shadow-lg p-6 border-2 border-gray-100 hover:border-green-300 hover:shadow-xl transition-all duration-200">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-gray-900 text-lg">{scenario.scenarioName}</h3>
                  <div className="bg-gradient-to-r from-green-100 to-emerald-100 px-3 py-1.5 rounded-full">
                    <span className="text-sm font-bold text-green-700">{(scenario.probability * 100).toFixed(0)}%</span>
                  </div>
                </div>
                <p className="text-gray-700 mb-4 leading-relaxed">{scenario.description}</p>
                {scenario.keyDrivers && scenario.keyDrivers.length > 0 && (
                  <div className="bg-gradient-to-br from-gray-50 to-green-50 p-4 rounded-xl">
                    <div className="text-xs font-bold text-green-900 mb-2 flex items-center gap-2">
                      <Lightbulb size={14} />
                      Key Drivers:
                    </div>
                    <ul className="space-y-2">
                      {scenario.keyDrivers.map((driver, i) => (
                        <li key={i} className="text-xs text-gray-700 flex items-start gap-2">
                          <span className="flex-shrink-0 w-5 h-5 bg-green-500 text-white rounded-full flex items-center justify-center text-xs font-bold">
                            {i + 1}
                          </span>
                          <span className="flex-1">{driver}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Model Performance & Info Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Model Performance */}
          {forecast.modelPerformance && (
            <div className="bg-white rounded-2xl shadow-lg p-6 border border-gray-100">
              <h2 className="text-xl font-bold text-gray-900 mb-4 flex items-center gap-2">
                <Activity className="text-blue-500" size={24} />
                Model Performance
              </h2>
              <div className="grid grid-cols-2 gap-4">
                {[
                  { label: 'MAE', value: forecast.modelPerformance.mae?.toFixed(2), color: 'from-blue-500 to-blue-600' },
                  { label: 'RMSE', value: forecast.modelPerformance.rmse?.toFixed(2), color: 'from-purple-500 to-purple-600' },
                  { label: 'MAPE', value: `${forecast.modelPerformance.mape?.toFixed(1)}%`, color: 'from-orange-500 to-orange-600' },
                  { label: 'R²', value: forecast.modelPerformance.rSquared?.toFixed(3), color: 'from-green-500 to-green-600' },
                ].map((metric, i) => (
                  <div key={i} className={`bg-gradient-to-br ${metric.color} p-4 rounded-xl text-white`}>
                    <div className="text-xs font-medium opacity-90 mb-1">{metric.label}</div>
                    <div className="text-2xl font-bold">{metric.value}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Key Assumptions & Warnings */}
          <div className="space-y-4">
            {forecast.keyAssumptions && forecast.keyAssumptions.length > 0 && (
              <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-2xl p-6 border border-blue-200">
                <h3 className="font-bold text-blue-900 mb-3 text-lg flex items-center gap-2">
                  <Lightbulb className="text-blue-600" size={20} />
                  Key Assumptions
                </h3>
                <ul className="space-y-2">
                  {forecast.keyAssumptions.map((assumption, i) => (
                    <li key={i} className="text-sm text-blue-800 flex items-start gap-2">
                      <span className="flex-shrink-0 w-5 h-5 bg-blue-600 text-white rounded-full flex items-center justify-center text-xs font-bold mt-0.5">
                        {i + 1}
                      </span>
                      <span className="flex-1">{assumption}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {forecast.riskWarnings && forecast.riskWarnings.length > 0 && (
              <div className="bg-gradient-to-br from-orange-50 to-red-50 rounded-2xl p-6 border border-orange-200">
                <h3 className="font-bold text-orange-900 mb-3 text-lg flex items-center gap-2">
                  <AlertTriangle className="text-orange-600" size={20} />
                  Risk Warnings
                </h3>
                <ul className="space-y-2">
                  {forecast.riskWarnings.map((warning, i) => (
                    <li key={i} className="text-sm text-orange-800 flex items-start gap-2">
                      <span className="flex-shrink-0 w-5 h-5 bg-orange-600 text-white rounded-full flex items-center justify-center text-xs font-bold mt-0.5">
                        !
                      </span>
                      <span className="flex-1">{warning}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForecastAnalysis;
