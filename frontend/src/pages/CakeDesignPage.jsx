import { useState, useRef, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { Sparkles, ImagePlus, X, CheckCircle, ChevronRight } from 'lucide-react';
import Button from '../components/Button';
import Input from '../components/Input';
import { cakeDesignApi } from '../api/cakeDesign';
import { sellersApi } from '../api/sellers';

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// Renders Gemini markdown (** bold **, * bullets) as proper JSX without any extra library
function renderInline(text) {
  const parts = text.split(/\*\*(.*?)\*\*/g);
  return parts.map((part, i) =>
    i % 2 === 1 ? <strong key={i}>{part}</strong> : part
  );
}

function BriefRenderer({ text }) {
  if (!text) return null;
  const lines = text.split('\n');
  return (
    <div className="brief-renderer">
      {lines.map((line, i) => {
        const trimmed = line.trim();
        if (!trimmed) return <div key={i} className="brief-spacer" />;
        if (/^[*•\-] /.test(trimmed)) {
          return (
            <div key={i} className="brief-bullet">
              <span>•</span>
              <span>{renderInline(trimmed.replace(/^[*•\-] /, ''))}</span>
            </div>
          );
        }
        return <div key={i} className="brief-line">{renderInline(trimmed)}</div>;
      })}
    </div>
  );
}

const composeSchema = z
  .object({
    description: z.string().min(20, 'Please describe your cake in at least 20 characters'),
    occasion: z.string().min(1, 'Occasion is required'),
    serves: z.coerce.number().int().min(1, 'Must serve at least 1'),
    budgetMin: z.coerce.number().positive('Enter a positive amount'),
    budgetMax: z.coerce.number().positive('Enter a positive amount'),
  })
  .refine((d) => d.budgetMax >= d.budgetMin, {
    message: 'Maximum must be ≥ minimum',
    path: ['budgetMax'],
  });

const orderSchema = z.object({
  sellerId: z.string().min(1, 'Please select a baker'),
});

const STAGE = {
  COMPOSE: 'compose',
  GENERATING: 'generating',
  PREVIEW: 'preview',
  ORDERING: 'ordering',
  SUCCESS: 'success',
};

export default function CakeDesignPage() {
  const [stage, setStage] = useState(STAGE.COMPOSE);
  const [preview, setPreview] = useState(null);
  const [savedCompose, setSavedCompose] = useState(null);
  const [referenceFile, setReferenceFile] = useState(null);
  const [refPreviewUrl, setRefPreviewUrl] = useState(null);
  const [sellers, setSellers] = useState([]);
  const [genError, setGenError] = useState('');
  const [orderError, setOrderError] = useState('');
  const fileRef = useRef(null);

  const composeForm = useForm({ resolver: zodResolver(composeSchema) });
  const orderForm = useForm({ resolver: zodResolver(orderSchema) });

  useEffect(() => {
    sellersApi.list().then((res) => setSellers(res || [])).catch(() => {});
  }, []);

  function handleFileSelect(file) {
    if (!file || !file.type.startsWith('image/')) return;
    setReferenceFile(file);
    setRefPreviewUrl(URL.createObjectURL(file));
  }

  function clearReference() {
    if (refPreviewUrl) URL.revokeObjectURL(refPreviewUrl);
    setReferenceFile(null);
    setRefPreviewUrl(null);
    if (fileRef.current) fileRef.current.value = '';
  }

  async function onGenerate(values) {
    setGenError('');
    setSavedCompose(values);
    setStage(STAGE.GENERATING);
    try {
      const referenceImageBase64 = referenceFile ? await fileToBase64(referenceFile) : null;
      const result = await cakeDesignApi.preview({
        description: values.description,
        occasion: values.occasion,
        serves: values.serves,
        budgetMin: values.budgetMin,
        budgetMax: values.budgetMax,
        referenceImageBase64,
      });
      setPreview(result);
      setStage(STAGE.PREVIEW);
    } catch (err) {
      setGenError(err?.response?.data?.message || err.message || 'Generation failed');
      setStage(STAGE.COMPOSE);
    }
  }

  async function onOrder(values) {
    if (stage !== STAGE.PREVIEW) return;
    setOrderError('');
    setStage(STAGE.ORDERING);
    try {
      await cakeDesignApi.confirm({
        sellerId: Number(values.sellerId),
        designBrief: preview.designBrief,
        imageBase64: preview.imageBase64,
        occasion: savedCompose.occasion,
        serves: Number(savedCompose.serves),
        budgetMin: Number(savedCompose.budgetMin),
        budgetMax: Number(savedCompose.budgetMax),
      });
      setStage(STAGE.SUCCESS);
    } catch (err) {
      setOrderError(err?.response?.data?.message || err.message || 'Could not send request');
      setStage(STAGE.PREVIEW);
    }
  }

  return (
    <div className="page ai-page">
      {stage === STAGE.COMPOSE && (
        <ComposeStage
          form={composeForm}
          onSubmit={onGenerate}
          referenceFile={referenceFile}
          refPreviewUrl={refPreviewUrl}
          onFileSelect={handleFileSelect}
          onClearRef={clearReference}
          fileRef={fileRef}
          error={genError}
        />
      )}
      {stage === STAGE.GENERATING && <GeneratingStage />}
      {(stage === STAGE.PREVIEW || stage === STAGE.ORDERING) && preview && (
        <PreviewStage
          preview={preview}
          orderForm={orderForm}
          onOrder={onOrder}
          onRetry={() => setStage(STAGE.COMPOSE)}
          sellers={sellers}
          ordering={stage === STAGE.ORDERING}
          error={orderError}
        />
      )}
      {stage === STAGE.SUCCESS && <SuccessStage />}
    </div>
  );
}

function ComposeStage({ form, onSubmit, referenceFile, refPreviewUrl, onFileSelect, onClearRef, fileRef, error }) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = form;

  return (
    <div className="ai-compose-card">
      <div className="ai-compose-header">
        <div className="ai-compose-intro">
          <Sparkles size={22} className="ai-sparkle-icon" />
          <p className="eyebrow" style={{ margin: 0 }}>AI Design Studio</p>
        </div>
        <h1>Design Your Dream Cake</h1>
        <p className="muted">Describe your vision and our AI will craft a personalised design and baker's brief.</p>
      </div>

      <form className="ai-compose-form" onSubmit={handleSubmit(onSubmit)}>
        <Input
          as="textarea"
          label="Describe your cake"
          placeholder="e.g. A three-tier wedding cake with blush pink roses, gold leaf accents, and a romantic garden feel..."
          rows={4}
          error={errors.description?.message}
          {...register('description')}
        />
        <Input
          label="Occasion"
          placeholder="e.g. Wedding, Birthday, Anniversary..."
          error={errors.occasion?.message}
          {...register('occasion')}
        />

        <div className="ai-budget-row">
          <Input
            type="number"
            label="Serves (people)"
            placeholder="e.g. 20"
            min={1}
            error={errors.serves?.message}
            {...register('serves')}
          />
          <Input
            type="number"
            label="Budget min (₹)"
            placeholder="e.g. 1500"
            min={1}
            error={errors.budgetMin?.message}
            {...register('budgetMin')}
          />
          <Input
            type="number"
            label="Budget max (₹)"
            placeholder="e.g. 3000"
            min={1}
            error={errors.budgetMax?.message}
            {...register('budgetMax')}
          />
        </div>

        <div className="field">
          <label>
            Reference photo{' '}
            <span className="ai-optional">(optional — helps the AI match your style)</span>
          </label>
          {refPreviewUrl ? (
            <div className="ai-reference-preview">
              <img src={refPreviewUrl} alt="Reference" className="ai-reference-img" />
              <button type="button" className="ai-reference-remove" onClick={onClearRef} aria-label="Remove reference image">
                <X size={11} />
              </button>
            </div>
          ) : (
            <label className="ai-reference-zone">
              <ImagePlus size={20} />
              <span>Click to upload a reference image</span>
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                onChange={(e) => onFileSelect(e.target.files?.[0])}
              />
            </label>
          )}
        </div>

        {error && <p className="error-message">{error}</p>}

        <Button type="submit" loading={isSubmitting} className="ai-generate-btn">
          <Sparkles size={15} /> Generate My Design
        </Button>
      </form>
    </div>
  );
}

function GeneratingStage() {
  return (
    <div className="ai-generating">
      <div>
        <p className="ai-generating-label">Crafting your design...</p>
        <p className="ai-generating-sub">
          Our AI is writing your baker's brief and painting your cake. This can take up to 30 seconds.
        </p>
      </div>
      <div className="shimmer-layout">
        <div className="shimmer-block shimmer-image" />
        <div className="shimmer-lines">
          <div className="shimmer-block shimmer-line long" />
          <div className="shimmer-block shimmer-line medium" />
          <div className="shimmer-block shimmer-line long" />
          <div className="shimmer-block shimmer-line short" />
          <div className="shimmer-block shimmer-line medium" />
          <div className="shimmer-block shimmer-line long" />
          <div className="shimmer-block shimmer-line medium" />
        </div>
      </div>
    </div>
  );
}

function PreviewStage({ preview, orderForm, onOrder, onRetry, sellers, ordering, error }) {
  const { register, handleSubmit, formState: { errors } } = orderForm;

  return (
    <div>
      <div className="ai-preview-header">
        <p className="eyebrow" style={{ marginBottom: 8 }}>
          <Sparkles size={13} style={{ verticalAlign: 'middle', marginRight: 4 }} />
          Your AI Design
        </p>
        <h2>Here's your cake design</h2>
        <p className="muted">Love it? Choose a baker and send your request below.</p>
      </div>

      <div className="ai-preview-layout">
        <div>
          {preview.imageBase64 ? (
            <img
              src={`data:${preview.mimeType};base64,${preview.imageBase64}`}
              alt="AI generated cake design"
              className="ai-preview-image"
            />
          ) : (
            <div className="ai-preview-image ai-no-image">
              <Sparkles size={32} />
              <p>Image preview unavailable right now. Your baker's brief is ready to send.</p>
            </div>
          )}
        </div>

        <div className="ai-order-form">
          <h3>Place your request</h3>
          <form onSubmit={handleSubmit(onOrder)}>
            <div style={{ display: 'grid', gap: 16 }}>
              <div className="field">
                <label>Choose a baker</label>
                <select className="input" {...register('sellerId')}>
                  <option value="">Select a baker...</option>
                  {sellers.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.shopName || `Baker #${s.id}`}
                    </option>
                  ))}
                </select>
                {errors.sellerId && <p className="field-error">{errors.sellerId.message}</p>}
              </div>

              {error && <p className="error-message">{error}</p>}

              <div className="ai-order-actions">
                <Button type="submit" loading={ordering}>
                  Send to Baker <ChevronRight size={15} />
                </Button>
                <button type="button" className="btn btn-ghost" onClick={onRetry} disabled={ordering}>
                  Try Again
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>

      <div className="ai-brief-box">
        <strong className="ai-brief-label">Baker's Brief</strong>
        <BriefRenderer text={preview.designBrief} />
      </div>
    </div>
  );
}

function SuccessStage() {
  return (
    <div className="ai-success">
      <CheckCircle size={52} className="ai-success-icon" />
      <div>
        <h2>Request sent!</h2>
        <p className="muted">
          Your custom cake request has been submitted. Your baker will review the AI-generated brief and
          get back to you with a quote.
        </p>
      </div>
      <div className="ai-success-actions">
        <Link to="/custom-orders" className="btn btn-primary">
          View My Requests <ChevronRight size={15} />
        </Link>
        <Link to="/sellers" className="btn btn-ghost">
          Browse More Bakers
        </Link>
      </div>
    </div>
  );
}
